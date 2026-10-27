package com.bharat.scholarship_rag_backend.entity;

import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "memories")
public class MemoryMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memoryId;

    /**
     * Persisted by name, not by ordinal: an ordinal column would silently
     * re-interpret every stored message if this enum is ever reordered.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole messageRole;

    @Column(nullable = false, columnDefinition = "text")
    private String context;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(384)")
    private float[] embeddings;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semantic_memory_id")
    private SemanticMemory semanticMemory;

}
