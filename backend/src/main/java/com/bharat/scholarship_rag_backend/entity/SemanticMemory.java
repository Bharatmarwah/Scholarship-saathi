package com.bharat.scholarship_rag_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "semantic_memories")
public class SemanticMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String conversationId;

    @OneToMany(fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            mappedBy = "semanticMemory")
    private List<MemoryMessage> memoryMessages;

    @Column(nullable = false)
    private Instant createdAt;
}