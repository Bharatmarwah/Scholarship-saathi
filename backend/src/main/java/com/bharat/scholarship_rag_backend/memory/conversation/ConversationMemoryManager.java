package com.bharat.scholarship_rag_backend.memory.conversation;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ConversationMemoryManager {

    private final ConversationMemory conversationMemory;

    public ConversationMemoryManager(ConversationMemory conversationMemory){
        this.conversationMemory=conversationMemory;
    }

    public void addUserMessage(ChatRequest chatRequest){

        ChatMessage message = new ChatMessage();
        message.setRole(MessageRole.USER);
        message.setContent(chatRequest.getQuery());
        message.setCreatedAt(LocalDateTime.now());

        conversationMemory.addMessage(chatRequest.getConversationId(),message);

    }

    public void addAssistantMessage(String conversationId ,ChatResponse chatResponse){
        ChatMessage message = new ChatMessage();
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(assistantContent(chatResponse));
        message.setCreatedAt(LocalDateTime.now());

        conversationMemory.addMessage(conversationId,message);
    }

    /**
     * The stored turn is the conversation summary when the model produced one,
     * otherwise the actual reply. Falling back matters because a grounded answer
     * that found no context carries no summary; without the fallback the
     * assistant turn would persist with a null content and vanish from the
     * history used by intent classification and query enrichment.
     */
    private static String assistantContent(ChatResponse chatResponse) {
        String summary = chatResponse.getConversationMemorySummary();
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return chatResponse.getResponse();
    }



}
