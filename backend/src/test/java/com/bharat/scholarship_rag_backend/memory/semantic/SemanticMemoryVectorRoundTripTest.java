package com.bharat.scholarship_rag_backend.memory.semantic;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the pgvector column end to end. The embedding is written through
 * JPA (which must bind it as a {@code vector}, not {@code bytea}) and read back
 * through the native query (which must bind it as a pgvector literal).
 */
@SpringBootTest
class SemanticMemoryVectorRoundTripTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private SemanticMemoryManager semanticMemoryManager;

    @Test
    void savesAndReadsBackEmbeddingAsVector() {
        String conversationId = UUID.randomUUID().toString();
        float[] embedding = unitVector();

        semanticMemoryManager.addSemanticMemory(new SemanticMemoryRequest(
                conversationId,
                "user context",
                embedding,
                "assistant context",
                embedding));

        List<SemanticMemoryResponse> memories =
                semanticMemoryManager.listAllSemanticMemories(conversationId, embedding);

        assertEquals(2, memories.size(), "expected both stored messages back");
        assertTrue(memories.stream().allMatch(memory -> memory.getDistance() != null
                && memory.getDistance() < 1e-6), "identical embeddings should be at distance 0");
    }

    private float[] unitVector() {
        float[] vector = new float[embeddingModel.dimension()];
        vector[0] = 1.0f;
        return vector;
    }
}
