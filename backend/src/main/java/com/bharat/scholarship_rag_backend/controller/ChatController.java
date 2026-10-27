package com.bharat.scholarship_rag_backend.controller;

import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.dto.response.ConversationIdResponse;
import com.bharat.scholarship_rag_backend.orchestration.ChatOrchestrator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api")
public class ChatController {

    private final ChatOrchestrator chatOrchestrator;

    public ChatController(ChatOrchestrator chatOrchestrator) {
        this.chatOrchestrator = chatOrchestrator;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody @Valid ChatRequest chatRequest) {
        return chatOrchestrator.processChat(chatRequest);
    }

    @GetMapping("/conversationId")
    public ConversationIdResponse conversationId(){
        String conversationId = UUID.randomUUID().toString();
        return new ConversationIdResponse(conversationId);
    }
}