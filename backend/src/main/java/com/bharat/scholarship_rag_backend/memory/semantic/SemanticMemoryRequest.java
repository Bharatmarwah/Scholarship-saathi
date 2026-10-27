package com.bharat.scholarship_rag_backend.memory.semantic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One semantic-memory exchange: the user context and the assistant context
 * recorded together, each stored as its own {@code MemoryMessage} (role,
 * context, embedding) under a single {@code SemanticMemory}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticMemoryRequest {

    private String conversationId;

    private String userContext;

    private float[] userEmbeddings;

    private String assistantContext;

    private float[] assistantEmbeddings;
}