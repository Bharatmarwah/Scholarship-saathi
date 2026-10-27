package com.bharat.scholarship_rag_backend.memory.conversation;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConversationMemoryManagerTest {

    private final ConversationMemory conversationMemory = mock(ConversationMemory.class);
    private final ConversationMemoryManager manager = new ConversationMemoryManager(conversationMemory);

    @Test
    void storedAssistantTurnPrefersConversationSummary() {
        manager.addAssistantMessage("c1", new ChatResponse("full reply", "semantic", "short summary"));

        assertEquals("short summary", capturedAssistantContent());
    }

    @Test
    void storedAssistantTurnFallsBackToResponseWhenSummaryMissing() {
        manager.addAssistantMessage("c1", new ChatResponse("full reply", null, null));

        assertEquals("full reply", capturedAssistantContent());
    }

    @Test
    void storedAssistantTurnFallsBackToResponseWhenSummaryBlank() {
        manager.addAssistantMessage("c1", new ChatResponse("full reply", null, "   "));

        assertEquals("full reply", capturedAssistantContent());
    }

    private String capturedAssistantContent() {
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(conversationMemory).addMessage(eq("c1"), captor.capture());
        return captor.getValue().getContent();
    }
}
