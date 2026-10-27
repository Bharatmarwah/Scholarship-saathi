package com.bharat.scholarship_rag_backend.memory.conversation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ConversationMemoryConstants {

    public static final String SESSION_PREFIX = "conversation:";

    private final Duration ttl;

    public ConversationMemoryConstants(@Value("${conversation.memory.ttl-hours}") long ttlHours) {
        this.ttl = Duration.ofHours(ttlHours);
    }

    public Duration getTtl() {
        return ttl;
    }
}