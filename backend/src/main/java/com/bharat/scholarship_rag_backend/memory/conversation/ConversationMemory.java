package com.bharat.scholarship_rag_backend.memory.conversation;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class ConversationMemory {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ConversationMemoryConstants conversationMemoryConstants;
    private final ObjectMapper objectMapper;

    public ConversationMemory(RedisTemplate<String, Object> redisTemplate,
                              ConversationMemoryConstants conversationMemoryConstants,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.conversationMemoryConstants = conversationMemoryConstants;
        this.objectMapper = objectMapper;
    }

    public void addMessage(String conversationId, ChatMessage message) {

        String key = ConversationMemoryConstants.SESSION_PREFIX + conversationId;

        try {
            redisTemplate
                    .opsForList()
                    .rightPush(key, objectMapper.writeValueAsString(message));

            redisTemplate.expire(key, conversationMemoryConstants.getTtl());
        } catch (Exception e) {
            // Conversation history is an optimisation, not the answer itself.
            // A Redis outage must not fail an otherwise answerable chat turn.
            log.warn("Failed to persist chat message to Redis; continuing without history", e);
        }
    }

    public List<ChatMessage> allRecentConversation(String conversationId) {
        String key = ConversationMemoryConstants.SESSION_PREFIX + conversationId;
        try {
            List<Object> messages = redisTemplate.opsForList().range(key, -10, -1);
            if (messages == null) {
                return List.of();
            }
            return messages.stream()
                    .map(this::toChatMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to read conversation history from Redis; treating it as empty", e);
            return List.of();
        }
    }

    private ChatMessage toChatMessage(Object entry) {
        if (entry instanceof ChatMessage message) {
            return message;
        }
        try {
            if (entry instanceof String json) {
                return objectMapper.readValue(json, ChatMessage.class);
            }
            return objectMapper.convertValue(entry, ChatMessage.class);
        } catch (Exception e) {
            log.warn("Skipping unreadable chat message in Redis", e);
            return null;
        }
    }
}