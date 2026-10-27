package com.bharat.scholarship_rag_backend.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Embeds text for retrieval.
 *
 * <p>Uses the single {@code embeddingModel} bean from {@code LlmConfig}, which
 * is also what ingestion will use, so query vectors and stored document vectors
 * stay comparable.
 */
@Component
public class QueryEmbedding {

    /**
     * Retrieval instruction for the bge family. It is prepended to the query
     * only; documents are embedded without it. Applying it on both sides, or on
     * neither, costs retrieval accuracy because the model was trained with the
     * instruction marking which side of the pair the short query is.
     */
    private static final String QUERY_INSTRUCTION =
            "Represent this sentence for searching relevant passages: ";

    private final EmbeddingModel embeddingModel;

    public QueryEmbedding(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Embeds a stored document (a chunk or a memory context). No instruction:
     * documents are indexed exactly as written.
     */
    public float[] embed(String query) {
        return embeddingModel.embed(query).content().vector();
    }

    /**
     * Embeds a user query for vector search. Adds the bge retrieval instruction
     * so the query vector lands where its matching document vectors are.
     */
    public float[] embedQuery(String query) {
        return embed(QUERY_INSTRUCTION + query);
    }

    /**
     * Embeds many stored documents in one call, without the query instruction.
     * Used by ingestion, where a whole corpus is embedded in a single batch.
     */
    public List<float[]> embedAll(List<String> texts) {
        return embeddingModel.embedAll(texts.stream()
                        .map(dev.langchain4j.data.segment.TextSegment::from)
                        .toList())
                .content().stream()
                .map(Embedding::vector)
                .toList();
    }

}