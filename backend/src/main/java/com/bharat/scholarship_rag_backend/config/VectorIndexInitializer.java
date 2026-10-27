package com.bharat.scholarship_rag_backend.config;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class VectorIndexInitializer {

    private final EntityManager entityManager;

    public VectorIndexInitializer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createHnswIndexes() {
        try {
            entityManager.createNativeQuery(
                    """
                    CREATE INDEX IF NOT EXISTS memories_embedding_hnsw_idx
                    ON memories USING hnsw (embedding vector_cosine_ops)
                    """
            ).executeUpdate();
            log.info("HNSW index wiring complete for memories.embedding");
        } catch (RuntimeException e) {
            log.error("Failed to create HNSW index on memories.embedding", e);
        }
    }
}