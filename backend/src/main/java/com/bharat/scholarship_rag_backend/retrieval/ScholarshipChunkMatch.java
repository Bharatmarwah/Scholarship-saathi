package com.bharat.scholarship_rag_backend.retrieval;

import java.util.Objects;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;

/**
 * A retrieved chunk together with its provenance and how it scored.
 *
 * @param distance       cosine distance to the query, null for keyword-only hits
 * @param keywordRank    {@code ts_rank} of the full-text match, null for
 *                       vector-only hits
 * @param score          fused ranking score used for ordering
 * @param citation       short human-readable reference for the answer, for
 *                       example {@code "Top Class PWD, guideline, page 12"}
 */
public record ScholarshipChunkMatch(
        UUID id,
        SchemeType scheme,
        String content,
        ContentType contentType,
        String sourceDocument,
        String sourceSection,
        String sourceSubsection,
        Integer pageStart,
        Integer pageEnd,
        Integer chunkIndex,
        Integer tokenCount,
        Double distance,
        Double keywordRank,
        double score,
        String citation) {

    /**
     * Copy of this match with {@code score} and {@code citation} filled in.
     * Kept as an explicit method so the record stays immutable.
     */
    public ScholarshipChunkMatch withRanking(double fusedScore, String reference) {
        return new ScholarshipChunkMatch(
                id, scheme, content, contentType, sourceDocument, sourceSection,
                sourceSubsection, pageStart, pageEnd, chunkIndex, tokenCount,
                distance, keywordRank, fusedScore, reference);
    }

    /** True when the vector arm found this chunk. */
    public boolean fromVector() {
        return distance != null;
    }

    /** True when the keyword arm found this chunk. */
    public boolean fromKeyword() {
        return keywordRank != null;
    }

    /** Page span rendered for display, or an empty string when unknown. */
    public String pageLabel() {
        if (pageStart == null && pageEnd == null) {
            return "";
        }
        if (Objects.equals(pageStart, pageEnd)) {
            return "page " + pageStart;
        }
        return "pages " + pageStart + "-" + pageEnd;
    }
}