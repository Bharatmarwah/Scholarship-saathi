package com.bharat.scholarship_rag_backend.retrieval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.repository.ScholarshipChunkRepo;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Finds the chunks that can answer a scholarship question.
 *
 * <p>Two arms run against the same query. The vector arm catches paraphrases
 * ("can more than two siblings get it" against a passage about sibling limits);
 * the full-text arm catches the exact tokens that matter for eligibility,
 * income ceilings and dates. Neither is reliable alone, so both run and their
 * rankings are fused with reciprocal rank fusion.
 *
 * <p>Fusion is used instead of adding normalised scores because {@code ts_rank}
 * and cosine distance live on unrelated scales. RRF only reads positions, so no
 * weight tuning is needed and a chunk that both arms like is promoted
 * automatically.
 *
 * <p>The scheme filter is a hard filter, never a ranking hint. A question about
 * one scheme must not return another scheme's rules, because the caller is
 * about to state them as fact.
 */
@Service
@Slf4j
public class ScholarshipRetrieval {

    /** Candidate depth per arm, wider than {@code topK} so fusion has room. */
    private static final int CANDIDATE_MULTIPLIER = 4;

    /** Minimum candidate depth, so small corpora still fill {@code topK}. */
    private static final int MIN_CANDIDATES = 8;

    /** Constant that damps the top of each ranking in RRF. */
    private static final int RRF_K = 60;

    /**
     * Cosine distance is {@code 1 - cosine_similarity}, so it runs {@code 0.0}
     * (identical direction, most similar) to {@code 2.0} (opposite direction,
     * least similar). The default cutoff is {@code 1.0} (similarity
     * {@code >= 0.0}): it keeps every non-negatively-correlated passage while
     * discarding opposite vectors, because relevant chunks commonly sit around
     * {@code 0.7-0.9} and the stricter {@code 0.8} cutoff was trimming them.
     * {@code 2.0} is kept as the upper clamp bound and means "gate off", where
     * the most-similar rows still win through {@code ORDER BY distance} +
     * {@code LIMIT}.
     */
    private static final double MAX_COSINE_DISTANCE = 2.0;

    /**
     * Normative text outranks a restatement of it. Applied after fusion so it
     * cannot distort the relative order inside either arm.
     */
    private static final Map<ContentType, Double> TYPE_WEIGHTS = Map.of(
            ContentType.GUIDELINE, 1.00,
            ContentType.ANNEXURE, 1.00,
            ContentType.FAQ, 0.90,
            ContentType.PORTAL_PAGE, 0.85);

    private final ScholarshipChunkRepo chunkRepo;
    private final QueryEmbedding queryEmbedding;
    private final int topK;
    private final double maxDistance;

    public ScholarshipRetrieval(
            ScholarshipChunkRepo chunkRepo,
            QueryEmbedding queryEmbedding,
            @Value("${retrieval.top-k:8}") int topK,
            @Value("${retrieval.max-distance:1.0}") double maxDistance) {
        this.chunkRepo = chunkRepo;
        this.queryEmbedding = queryEmbedding;
        this.topK = topK;
        this.maxDistance = clampDistance(maxDistance);
    }

    /**
     * Restricts the cutoff to the cosine-distance range {@code [0, 2]}. A value
     * outside it is a misconfiguration and is clamped rather than trusted, so
     * the SQL filter keeps its intended meaning.
     */
    static double clampDistance(double value) {
        return Math.max(0.0, Math.min(MAX_COSINE_DISTANCE, value));
    }

    /**
     * Retrieves chunks for an already-reformulated query.
     *
     * @param query      text embedded for the vector arm and parsed for the
     *                   full-text arm
     * @param candidates schemes to search; an empty list means discovery mode
     *                   and searches every scheme
     * @return best matches, highest score first, never null
     */
    public List<ScholarshipChunkMatch> search(String query, List<SchemeType> candidates) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        boolean scoped = candidates != null && !candidates.isEmpty();
        String schemeArray = PgVectorLiteral.array(
                scoped ? candidates.stream().map(Enum::name).toList() : List.of());
        int candidateDepth = Math.max(MIN_CANDIDATES, topK * CANDIDATE_MULTIPLIER);

        // The keyword arm does not need the embedding, so both arms run even if
        // one of them fails; a broken vector extension should not silence search
        // outright.
        ArmResult vector = runVectorArm(query, schemeArray, scoped, candidateDepth);
        ArmResult keyword = runKeywordArm(query, schemeArray, scoped, candidateDepth);
        List<Object[]> vectorRows = vector.rows();
        List<Object[]> keywordRows = keyword.rows();

        logVectorDistances(vectorRows);

        // An empty result is only "no evidence" when at least one arm actually
        // ran. If both failed, the corpus may well contain the answer and the
        // caller must not tell the student the information does not exist.
        if (vector.failed() && keyword.failed()) {
            throw new RetrievalUnavailableException(
                    "Both scholarship retrieval arms failed");
        }

        if (vectorRows.isEmpty() && keywordRows.isEmpty()) {
            return List.of();
        }

        return fuse(vectorRows, keywordRows).stream()
                .limit(topK)
                .toList();
    }

    private ArmResult runVectorArm(String query, String schemeArray, boolean scoped, int limit) {
        try {
            float[] vector = queryEmbedding.embedQuery(query);
            String literal = PgVectorLiteral.vector(vector);
            if (literal == null) {
                log.warn("Query embedding was empty; vector arm cannot run");
                return new ArmResult(List.of(), true);
            }
            List<Object[]> rows = chunkRepo.searchByVector(
                    literal, schemeArray, scoped, maxDistance, limit);
            return new ArmResult(rows == null ? List.of() : rows, false);
        } catch (RuntimeException e) {
            log.warn("Vector arm of scholarship retrieval failed, falling back to full text", e);
            return new ArmResult(List.of(), true);
        }
    }

    /**
     * Reports where the retrieved passages actually sit on the cosine-distance
     * scale ({@code 0 = identical, 2 = opposite}), so the cutoff can be
     * calibrated from data instead of guessed. No log line is emitted when the
     * vector arm returned nothing.
     */
    private void logVectorDistances(List<Object[]> vectorRows) {
        if (vectorRows.isEmpty()) {
            return;
        }

        double[] distances = new double[vectorRows.size()];
        int n = 0;
        for (Object[] row : vectorRows) {
            Double distance = asDouble(row.length > 11 ? row[11] : null);
            if (distance != null) {
                distances[n++] = distance;
            }
        }
        if (n == 0) {
            return;
        }
        Arrays.sort(distances, 0, n);
        double p50 = distances[percentileIndex(n, 50)];
        double p90 = distances[percentileIndex(n, 90)];
        double p99 = distances[percentileIndex(n, 99)];
        double furthest = distances[n - 1];

        log.info(String.format(Locale.ROOT,
                "Vector-arm cosine distances: n=%d p50=%.2f p90=%.2f p99=%.2f furthest=%.2f cutoff=%.2f",
                n, p50, p90, p99, furthest, maxDistance));
    }

    private static int percentileIndex(int size, int percentile) {
        return Math.max(0, Math.min(size - 1,
                (int) Math.ceil(percentile / 100.0 * size) - 1));
    }

    private ArmResult runKeywordArm(String query, String schemeArray, boolean scoped, int limit) {
        try {
            List<Object[]> rows = chunkRepo.searchByKeyword(query, schemeArray, scoped, limit);
            return new ArmResult(rows == null ? List.of() : rows, false);
        } catch (RuntimeException e) {
            log.warn("Full-text arm of scholarship retrieval failed", e);
            return new ArmResult(List.of(), true);
        }
    }

    /** One retrieval arm's rows, and whether the arm itself failed to run. */
    private record ArmResult(List<Object[]> rows, boolean failed) {
    }

    /**
     * Combines both ranked lists into one ordering.
     *
     * <p>A chunk found by both arms scores the sum of its two reciprocal ranks,
     * so agreement between a semantic and a lexical match is what lifts it to
     * the top. Duplicates are merged rather than returned twice: the same
     * passage legitimately appears in both result sets.
     */
    List<ScholarshipChunkMatch> fuse(List<Object[]> vectorRows, List<Object[]> keywordRows) {

        Map<UUID, ScholarshipChunkMatch> merged = new LinkedHashMap<>();
        Map<UUID, Double> scores = new LinkedHashMap<>();
        // Different chunk rows can carry the same text (a guideline section
        // repeated in an annexure, or overlapping windows). Sending duplicates
        // to the answer model only wastes context and makes it look like a rule
        // is stated twice, so identical content within one scheme is kept once.
        java.util.Set<String> seenContent = new java.util.HashSet<>();

        absorb(vectorRows, true, merged, scores, seenContent);
        absorb(keywordRows, false, merged, scores, seenContent);

        List<ScholarshipChunkMatch> ranked = new ArrayList<>(merged.size());
        for (Map.Entry<UUID, ScholarshipChunkMatch> entry : merged.entrySet()) {
            ScholarshipChunkMatch match = entry.getValue();
            double weight = TYPE_WEIGHTS.getOrDefault(match.contentType(), 1.0);
            ranked.add(match.withRanking(scores.get(entry.getKey()) * weight,
                    citation(match)));
        }

        ranked.sort(Comparator.comparingDouble(ScholarshipChunkMatch::score).reversed()
                // Stable tie-break on provenance keeps ordering deterministic
                // across runs, which matters for reproducible answers.
                .thenComparing(ScholarshipChunkMatch::sourceDocument,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(match -> match.chunkIndex() == null ? 0 : match.chunkIndex()));

        log.info("Fused {} vector and {} keyword rows into {} chunks",
                vectorRows.size(), keywordRows.size(), ranked.size());
        return ranked;
    }

    private void absorb(List<Object[]> rows,
                        boolean vectorArm,
                        Map<UUID, ScholarshipChunkMatch> merged,
                        Map<UUID, Double> scores,
                        java.util.Set<String> seenContent) {

        int rank = 0;
        for (Object[] row : rows) {
            rank++;

            ScholarshipChunkMatch match = map(row, vectorArm);
            if (match == null || match.id() == null) {
                continue;
            }

            // The same chunk id arriving from both arms must still accumulate
            // both reciprocal-rank contributions, so key the content guard on
            // genuinely different rows only.
            boolean alreadyMergedById = merged.containsKey(match.id());
            if (!alreadyMergedById) {
                String fingerprint = (match.scheme() == null ? "" : match.scheme().name())
                        + "|" + match.content().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                if (!seenContent.add(fingerprint)) {
                    continue;
                }
            }

            merged.putIfAbsent(match.id(), match);
            scores.merge(match.id(), 1.0 / (RRF_K + rank), Double::sum);
        }
    }

    /**
     * Maps a native row onto the match record.
     *
     * <p>Column positions follow the {@code SELECT} list in
     * {@link ScholarshipChunkRepo} and are read by index. Every conversion is
     * defensive because a native query hands back whatever the driver produced,
     * including {@code null}s and enum names as strings.
     *
     * @return the match, or null when the row is unusable
     */
    ScholarshipChunkMatch map(Object[] row, boolean vectorArm) {

        if (row == null || row.length < 12) {
            return null;
        }

        SchemeType scheme = scheme(row[1]);
        String content = asString(row[2]);
        if (content == null || content.isBlank()) {
            return null;
        }

        return new ScholarshipChunkMatch(
                asUuid(row[0]),
                scheme,
                content,
                contentType(row[3]),
                asString(row[4]),
                asString(row[5]),
                asString(row[6]),
                asInteger(row[7]),
                asInteger(row[8]),
                asInteger(row[9]),
                asInteger(row[10]),
                vectorArm ? asDouble(row[11]) : null,
                vectorArm ? null : asDouble(row[11]),
                0.0,
                null);
    }

    /** Scheme enum value, or null when the stored value is not recognised. */
    static SchemeType scheme(Object value) {
        String name = asString(value);
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return SchemeType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Content type enum value, defaulting to guideline when unreadable. */
    static ContentType contentType(Object value) {
        String name = asString(value);
        if (name == null || name.isBlank()) {
            return ContentType.GUIDELINE;
        }
        try {
            return ContentType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ContentType.GUIDELINE;
        }
    }

    private static String citation(ScholarshipChunkMatch match) {
        StringBuilder citation = new StringBuilder(
                match.scheme() == null
                        ? "Scholarship document"
                        : match.scheme().name().replace('_', ' '));

        String section = match.sourceSection();
        if (section != null && !section.isBlank()) {
            citation.append(", ").append(section.strip());
        }

        String page = match.pageLabel();
        if (!page.isEmpty()) {
            citation.append(", ").append(page);
        }
        return citation.toString();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID asUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}