package com.bharat.scholarship_rag_backend.chunking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the cleaned corpus and dispatches each document to the chunker that
 * matches its type.
 *
 * <p>This deliberately stops short of persistence. Nothing here embeds or
 * writes to a database: the output is a reviewable {@link TextChunk} list so
 * the chunk boundaries can be checked against the source before any table exists
 * to hold them.
 *
 * <p>Documents are read from the classpath rather than from
 * {@code src/main/resources}. The corpus is a packaged resource, so this works
 * the same whether the application is started from the module directory, from a
 * different working directory, or from a packaged jar.
 */
@Component
public final class CorpusChunker {

    /** Location of the cleaned corpus on the classpath. */
    static final String CLASSPATH_CORPUS = "classpath*:cleaned-markdown/**/*.md";

    /** Marker used to recover the {@code <slug>/<name>.md} id from a resource URL. */
    private static final String CORPUS_MARKER = "cleaned-markdown/";

    private final TokenCounter counter;
    private final GuidelineChunker guidelineChunker;
    private final FaqChunker faqChunker;
    private final SchemeRegistry registry;

    /**
     * Used by the command-line entry point and by tests, which have no Spring
     * context. Both dependencies are cheap to construct: {@link TokenCounter}
     * only builds a local tokenizer and {@link SchemeRegistry} is a static
     * catalogue.
     */
    public CorpusChunker() {
        this(new TokenCounter(), new SchemeRegistry());
    }

    @Autowired
    public CorpusChunker(TokenCounter counter, SchemeRegistry registry) {
        this.counter = counter;
        this.guidelineChunker = new GuidelineChunker(counter);
        this.faqChunker = new FaqChunker(counter);
        this.registry = registry;
    }

    public List<TextChunk> chunkAll() {
        List<TextChunk> chunks = new ArrayList<>();
        for (SourceDocument document : loadCorpus()) {
            chunks.addAll(document.contentType() == ContentType.FAQ
                    ? faqChunker.chunk(document)
                    : guidelineChunker.chunk(document));
        }
        return chunks;
    }

    /**
     * Walks {@code cleaned-markdown/<scheme-slug>/<name>.md} on the classpath.
     * The slug resolves through {@link SchemeRegistry} so the corpus cannot
     * drift from the set of schemes the validator knows.
     *
     * <p>Results are sorted by document id so repeated runs produce the same
     * chunk order and therefore the same chunk indexes.
     */
    public List<SourceDocument> loadCorpus() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver()
                    .getResources(CLASSPATH_CORPUS);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to locate the cleaned corpus on the classpath", e);
        }

        if (resources.length == 0) {
            throw new IllegalStateException("No cleaned corpus found at " + CLASSPATH_CORPUS
                    + ". Expected the Markdown files under src/main/resources/cleaned-markdown.");
        }

        List<SourceDocument> documents = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            String documentId = documentId(resource);
            int slash = documentId.indexOf('/');
            if (slash <= 0) {
                throw new IllegalStateException(
                        "Corpus document '" + documentId + "' is not under a scheme directory");
            }

            String slug = documentId.substring(0, slash);
            String fileName = documentId.substring(slash + 1);

            SchemeType scheme = resolveScheme(slug);
            if (scheme == null) {
                throw new IllegalStateException("Corpus directory '" + slug
                        + "' does not resolve to a known scheme");
            }

            String content = read(resource, documentId);
            documents.add(new SourceDocument(
                    scheme,
                    documentId,
                    fileName.toLowerCase(Locale.ROOT).contains("faq")
                            ? ContentType.FAQ
                            : ContentType.GUIDELINE,
                    MarkdownStructureParser.academicYear(content),
                    content));
        }

        documents.sort(Comparator.comparing(SourceDocument::documentId));
        return documents;
    }

    /**
     * A digest of the whole corpus, used to decide whether a previously chunked
     * run is still current.
     *
     * <p>Hashing the documents is far cheaper than chunking them, so this can be
     * computed on every startup to skip the expensive tokenizer pass when
     * nothing has changed. Editing any document changes the digest and triggers
     * a re-chunk; leaving the corpus alone does not.
     */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (SourceDocument document : loadCorpus()) {
                digest.update(document.documentId().getBytes(StandardCharsets.UTF_8));
                digest.update(document.markdown().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Recovers {@code <slug>/<name>.md} from a classpath resource. Works for
     * both an exploded classpath and a packaged jar, where the URL looks like
     * {@code jar:file:/app.jar!/BOOT-INF/classes/cleaned-markdown/...}.
     */
    private static String documentId(Resource resource) {
        String path;
        try {
            path = resource.getURL().getPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resource URL for a corpus document", e);
        }

        int marker = path.lastIndexOf(CORPUS_MARKER);
        String documentId = marker < 0
                ? Objects.requireNonNull(resource.getFilename())
                : path.substring(marker + CORPUS_MARKER.length());

        return documentId.replace('\\', '/');
    }

    private static String read(Resource resource, String documentId) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read corpus document " + documentId, e);
        }
    }

    private SchemeType resolveScheme(String slug) {
        List<SchemeType> resolved = registry.resolveAll(slug);
        return resolved.size() == 1 ? resolved.get(0) : null;
    }

    /**
     * Writes a human-reviewable dump of the chunks and returns where it landed.
     * No embedding, no database.
     */
    public Path writeReviewDump(List<TextChunk> chunks, Path out) {
        try {
            Path parent = out.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            StringBuilder report = new StringBuilder();
            for (TextChunk chunk : chunks) {
                report.append("=".repeat(100)).append('\n');
                report.append("scheme=").append(chunk.scheme())
                        .append(" doc=").append(chunk.documentId())
                        .append(" type=").append(chunk.contentType())
                        .append(" year=").append(chunk.academicYear())
                        .append(" pages=").append(chunk.pageStart()).append('-').append(chunk.pageEnd())
                        .append(" section=").append(chunk.section())
                        .append(" sub=").append(chunk.subsection())
                        .append(" idx=").append(chunk.chunkIndex())
                        .append(" tokens=").append(chunk.tokenCount())
                        .append('\n');
                report.append("-".repeat(100)).append('\n');
                report.append(chunk.content()).append("\n\n");
            }
            Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write the chunk review dump to " + out, e);
        }
    }

    /** Command-line entry point. Spring uses {@code CorpusChunkRunner} instead. */
    public static void main(String[] args) throws IOException {
        CorpusChunker chunker = new CorpusChunker();
        List<TextChunk> chunks = chunker.chunkAll();

        Path out = Path.of(args.length > 0 ? args[0] : ".chunking/chunk-review.txt");
        chunker.writeReviewDump(chunks, out);

        System.out.println(new ChunkingReport(chunks).render());
        System.out.println("Full dump written to " + out.toAbsolutePath());
    }

    /** Counts chunks per document and summarises the token distribution. */
    record ChunkingReport(List<TextChunk> chunks) {

        String render() {
            StringBuilder out = new StringBuilder();

            out.append("CHUNKS PER DOCUMENT\n");
            Map<String, List<TextChunk>> byDocument = new LinkedHashMap<>();
            for (TextChunk chunk : chunks) {
                byDocument.computeIfAbsent(chunk.documentId(), k -> new ArrayList<>()).add(chunk);
            }
            for (Map.Entry<String, List<TextChunk>> entry : byDocument.entrySet()) {
                List<TextChunk> group = entry.getValue();
                out.append(String.format("  %-46s %4d chunks  pages %d-%d  year=%s%n",
                        entry.getKey(), group.size(),
                        group.stream().map(TextChunk::pageStart).filter(Objects::nonNull)
                                .mapToInt(Integer::intValue).min().orElse(0),
                        group.stream().map(TextChunk::pageEnd).filter(Objects::nonNull)
                                .mapToInt(Integer::intValue).max().orElse(0),
                        group.get(0).academicYear()));
            }

            out.append("\nTOKEN DISTRIBUTION\n");
            int[] tokens = chunks.stream().map(TextChunk::tokenCount)
                    .mapToInt(Integer::intValue).sorted().toArray();
            int total = 0;
            for (int t : tokens) {
                total += t;
            }
            out.append(String.format("  chunks=%d  total=%d  min=%d  p50=%d  p90=%d  p99=%d  max=%d  mean=%.1f%n",
                    tokens.length, total, tokens[0],
                    percentile(tokens, 50), percentile(tokens, 90),
                    percentile(tokens, 99), tokens[tokens.length - 1],
                    (double) total / tokens.length));

            Map<String, Integer> histogram = new LinkedHashMap<>();
            for (int t : tokens) {
                String bucket = t <= 200 ? "000-200" : t <= 400 ? "201-400"
                        : t <= 600 ? "401-600" : t <= 800 ? "601-800" : "801+";
                histogram.merge(bucket, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> bucket : histogram.entrySet()) {
                out.append(String.format("  %-9s %4d %s%n", bucket.getKey(), bucket.getValue(),
                        "#".repeat(Math.min(60, bucket.getValue()))));
            }

            long pageSpanning = chunks.stream().filter(TextChunk::spansPages).count();
            long oversized = chunks.stream().filter(c -> c.tokenCount() > 800).count();
            long tiny = chunks.stream().filter(c -> c.tokenCount() < 60).count();
            out.append(String.format("%n  chunks spanning a page break: %d%n", pageSpanning));
            out.append(String.format("  chunks over the 800-token ceiling: %d%n", oversized));
            out.append(String.format("  chunks under 60 tokens: %d%n", tiny));

            return out.toString();
        }

        /** Chunk totals the runner logs as one line. */
        String summary() {
            int[] tokens = chunks.stream().map(TextChunk::tokenCount)
                    .mapToInt(Integer::intValue).sorted().toArray();
            int total = 0;
            for (int t : tokens) {
                total += t;
            }
            return String.format("chunks=%d totalTokens=%d min=%d p50=%d p90=%d max=%d mean=%.1f",
                    tokens.length, total, tokens[0],
                    percentile(tokens, 50), percentile(tokens, 90),
                    tokens[tokens.length - 1], (double) total / tokens.length);
        }

        private static int percentile(int[] sorted, int percentile) {
            int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }
    }
}