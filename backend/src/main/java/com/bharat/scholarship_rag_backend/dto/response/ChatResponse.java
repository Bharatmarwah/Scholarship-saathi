package com.bharat.scholarship_rag_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private String response;
    private String semanticMemorySummary;
    private String conversationMemorySummary;


    public static ChatResponse noResult(String answer) {
        return new ChatResponse(answer, null, null);
    }

}