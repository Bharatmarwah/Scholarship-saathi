package com.bharat.scholarship_rag_backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    private MessageRole role;
    private String content;
    private LocalDateTime createdAt;

}
