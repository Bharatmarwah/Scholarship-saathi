package com.bharat.scholarship_rag_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatRequest {

    /*
     * One conversationId maps to exactly one student profile, so it is the
     * identity of everything stored for the student. It must not carry
     * padding whitespace ("abc " would silently become a second profile) and
     * must fit the 255-character column. The id is issued by
     * GET /v1/api/conversationId as a UUID, but a client may supply its own.
     */
    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^\\S+$", message = "conversationId must not contain whitespace")
    private String conversationId;

    @NotBlank
    @Size(max = 4000, message = "query must be at most 4000 characters")
    private String query;

    /*
     * If true, stream the LLM response.
     * If false, return the complete response.
     */
    private boolean stream = false;

}
