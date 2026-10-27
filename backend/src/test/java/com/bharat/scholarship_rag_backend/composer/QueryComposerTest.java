package com.bharat.scholarship_rag_backend.composer;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryComposerTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private QueryComposer composer() {
        return new QueryComposer(chatModel, objectMapper, new SchemeRegistry());
    }

    @Test
    void promptCarriesTheValueNormalizationContract() {
        when(chatModel.chat(anyString())).thenReturn(
                "{\"combinedQuery\":\"q\",\"targetSchemes\":[],\"extractedValues\":{}}");

        composer().compose("I am in 3rd year of B.Tech", List.<ChatMessage>of(), List.of());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(captor.capture());
        String prompt = captor.getValue();

        assertTrue(prompt.contains("Return normalized values"));
        assertTrue(prompt.contains("\"5 lakh\""));
        assertTrue(prompt.contains("plain integer"));
    }

    @Test
    void passesNormalizedValuesThroughUntouched() {
        when(chatModel.chat(anyString())).thenReturn(
                "{\"combinedQuery\":\"q\",\"targetSchemes\":[],"
                        + "\"extractedValues\":{\"CURRENT_YEAR\":3,\"ANNUAL_FAMILY_INCOME\":500000}}");

        QueryComposerResult result = composer().compose(
                "I am in 3rd year, family income 5 lakh", List.<ChatMessage>of(), List.of());

        assertEquals(3, result.getExtractedValues().get("CURRENT_YEAR"));
        assertEquals(500000, result.getExtractedValues().get("ANNUAL_FAMILY_INCOME"));
    }

    @Test
    void fallsBackToTheOriginalQueryWhenTheResponseCannotBeParsed() {
        when(chatModel.chat(anyString())).thenReturn("not json");

        QueryComposerResult result = composer().compose(
                "original query",
                List.of(new ChatMessage(MessageRole.USER, "hi", LocalDateTime.now())),
                List.of());

        assertEquals("original query", result.getCombinedQuery());
        assertEquals(Map.of(), result.getExtractedValues());
        assertTrue(result.getTargetSchemes().isEmpty());
    }
}
