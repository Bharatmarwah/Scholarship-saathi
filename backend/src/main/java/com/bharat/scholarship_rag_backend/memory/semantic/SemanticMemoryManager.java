package com.bharat.scholarship_rag_backend.memory.semantic;

import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import com.bharat.scholarship_rag_backend.entity.MemoryMessage;
import com.bharat.scholarship_rag_backend.entity.SemanticMemory;
import com.bharat.scholarship_rag_backend.repository.SemanticMemoryRepo;
import com.bharat.scholarship_rag_backend.retrieval.PgVectorLiteral;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class SemanticMemoryManager {

    @Value("${retrieval.top-k:5}")
    private Integer topK;

    /**
     * Distance below which a stored memory counts as related. Kept separate
     * from {@code retrieval.max-distance} (chunk retrieval) because a memory
     * must be more clearly on-topic than a chunk to be worth recalling.
     */
    @Value("${semantic-memory.max-distance:0.30}")
    private double maxDistance;

    private final SemanticMemoryRepo semanticMemoryRepo;

    public SemanticMemoryManager(SemanticMemoryRepo semanticMemoryRepo) {
        this.semanticMemoryRepo = semanticMemoryRepo;
    }

    @Transactional
    public void addSemanticMemory(SemanticMemoryRequest semanticMemoryRequest) {
        SemanticMemory memory = new SemanticMemory();

        memory.setConversationId(semanticMemoryRequest.getConversationId());
        memory.setCreatedAt(Instant.now());

        MemoryMessage userMessage = buildMessage(
                MessageRole.USER,
                semanticMemoryRequest.getUserContext(),
                semanticMemoryRequest.getUserEmbeddings());

        MemoryMessage assistantMessage = buildMessage(
                MessageRole.ASSISTANT,
                semanticMemoryRequest.getAssistantContext(),
                semanticMemoryRequest.getAssistantEmbeddings());

        userMessage.setSemanticMemory(memory);
        assistantMessage.setSemanticMemory(memory);

        memory.setMemoryMessages(List.of(userMessage, assistantMessage));

        semanticMemoryRepo.save(memory);
    }

    @Transactional
    public List<SemanticMemoryResponse> listAllSemanticMemories(String conversationId, float[] queryEmbedding) {
        try {
            String literal = PgVectorLiteral.vector(queryEmbedding);
            return semanticMemoryRepo.findSimilarMemories(conversationId, literal, maxDistance, topK)
                    .stream()
                    .map(row -> new SemanticMemoryResponse(row.getContext(), row.getDistance()))
                    .toList();
        } catch (RuntimeException e) {
            // Long-term memory enriches a turn; it is not required to answer.
            // A DB/pgvector failure must degrade to "no memories" instead of
            // turning the whole chat request into a 500.
            log.warn("Failed to load semantic memories; continuing without them", e);
            return List.of();
        }
    }

    private MemoryMessage buildMessage(MessageRole role, String context, float[] embeddings) {
        MemoryMessage message = new MemoryMessage();
        message.setMessageRole(role);
        message.setContext(context);
        message.setEmbeddings(embeddings);
        return message;
    }
}