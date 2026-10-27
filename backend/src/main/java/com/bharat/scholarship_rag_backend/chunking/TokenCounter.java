package com.bharat.scholarship_rag_backend.chunking;

import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.springframework.stereotype.Component;

/**
 * Token counts for chunk budgeting, measured with the real byte-pair encoder
 * rather than estimated from character counts.
 *
 * <p>Chunk boundaries are chosen against a token budget, so the unit has to be
 * the token the embedding model will actually produce. The estimator wraps
 * jtokkit's bundled {@code cl100k_base} vocabulary, which ships inside the
 * jar, so this works offline and needs no API call. The alternative —
 * {@code chars / 4} — drifts far enough on this corpus to matter: it
 * under-counts the dense numeric tables and over-counts the whitespace-heavy
 * annexure rows, which is exactly where the budget must not slip.
 */
@Component
public class TokenCounter {

    private final OpenAiTokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4");

    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return estimator.estimateTokenCountInText(text);
    }
}