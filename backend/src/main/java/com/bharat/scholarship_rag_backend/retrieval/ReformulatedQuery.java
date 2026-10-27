package com.bharat.scholarship_rag_backend.retrieval;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;

import java.util.List;

/**
 * Result of {@link RetrievalQueryReformulator}: the text to embed together
 * with the schemes the answer must come from.
 *
 * <p>The scheme travels with the query so that it cannot be dropped between
 * reformulation and retrieval. Callers are expected to use
 * {@link #candidates()} as a hard filter on the knowledge base.
 *
 * @param query      text that is embedded for retrieval
 * @param scheme     scheme named this turn, null in discovery mode
 * @param candidates schemes to filter on: the single named scheme, or every
 *                   scheme in discovery mode
 */
public record ReformulatedQuery(
        String query,
        SchemeType scheme,
        List<SchemeType> candidates) {

    /**
     * Unscoped query, used when no scheme could be resolved and no candidate
     * list is available.
     */
    public static ReformulatedQuery unscoped(String query) {
        return new ReformulatedQuery(query, null, List.of());
    }

    /**
     * Names of {@link #candidates()}, for logging and prompt rendering.
     */
    public List<String> schemeNames() {
        return candidates.stream().map(Enum::name).toList();
    }

    public boolean isScoped() {
        return !candidates.isEmpty();
    }
}