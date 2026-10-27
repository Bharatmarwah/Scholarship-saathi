package com.bharat.scholarship_rag_backend.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryComposerResult {

    private String combinedQuery;

    /**
     * Every scheme referred to in the inputs, as free-text names exactly as
     * the model returned them. A comparison question carries more than one.
     * Resolution to {@code SchemeType} happens only in the scheme registry,
     * never from these strings directly.
     */
    private List<String> targetSchemes;

    private Map<String, Object> extractedValues;

    /**
     * True when the composer merged history or memory into the combined query
     * to make a follow-up self-contained. False when the latest query was
     * already self-contained. The orchestrator uses this instead of guessing
     * from the query text alone.
     */
    private Boolean usedContext;

    public QueryComposerResult(
            String combinedQuery,
            List<String> targetSchemes,
            Map<String, Object> extractedValues) {
        this(combinedQuery, targetSchemes, extractedValues, false);
    }

    public QueryComposerResult(
            String combinedQuery,
            List<String> targetSchemes,
            Map<String, Object> extractedValues,
            Boolean usedContext) {
        this.combinedQuery = combinedQuery;
        this.targetSchemes = targetSchemes;
        this.extractedValues = extractedValues;
        this.usedContext = usedContext;
    }

    /** Null-safe read; an absent flag means the composer did not use context. */
    public boolean isUsedContext() {
        return Boolean.TRUE.equals(usedContext);
    }

    public static QueryComposerResult fallback(String combinedQuery) {
        return new QueryComposerResult(combinedQuery, List.of(), Map.of(), false);
    }
}