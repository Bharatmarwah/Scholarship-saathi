package com.bharat.scholarship_rag_backend.memory.semantic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticMemoryResponse {
    private String context;
    private Double distance;
}