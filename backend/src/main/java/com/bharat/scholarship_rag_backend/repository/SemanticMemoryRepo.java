package com.bharat.scholarship_rag_backend.repository;

import com.bharat.scholarship_rag_backend.entity.SemanticMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the semantic memory.
 *
 * <p>As in {@link ScholarshipChunkRepo}, the embedding is passed as a pgvector
 * text literal ({@code "[0.1,0.2]"}) rather than a {@code float[]}:
 * {@code CAST(float8[] AS vector)} is not a valid cast, so binding the array
 * directly fails at runtime. Rows are projected onto
 * {@link SemanticMemoryRow} so the caller never indexes {@code Object[]} columns.
 */
public interface SemanticMemoryRepo extends JpaRepository<SemanticMemory, UUID> {

    /**
     * @param queryEmbedding pgvector literal, for example {@code "[0.1,0.2]"}
     * @param maxDistance    distance below which a memory is recalled
     * @return closest matching memories, nearest first
     */
    @Query(value = """
            SELECT
                m.context AS context,
                m.embedding <=> CAST(:queryEmbedding AS vector) AS distance
            FROM memories m
            JOIN semantic_memories sm ON sm.id = m.semantic_memory_id
            WHERE sm.conversation_id = :conversationId
              AND m.embedding <=> CAST(:queryEmbedding AS vector) <= :maxDistance
            ORDER BY m.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<SemanticMemoryRow> findSimilarMemories(
            @Param("conversationId") String conversationId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("maxDistance") double maxDistance,
            @Param("topK") Integer topK
    );
}