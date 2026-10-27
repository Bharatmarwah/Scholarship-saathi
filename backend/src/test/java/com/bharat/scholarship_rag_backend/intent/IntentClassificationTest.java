package com.bharat.scholarship_rag_backend.intent;

import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentClassificationTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void feedsConversationHistoryIntoThePromptForAnAnsweringTurn() {
        ChatMessage validationQuestion = new ChatMessage(
                MessageRole.ASSISTANT,
                "Could you please tell me which year of your course you are currently in?",
                LocalDateTime.now());

        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"type\":\"SCHOLARSHIP\"}");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify(
                "I am in 3rd year of B.Tech.",
                List.of(validationQuestion));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(captor.capture());
        String prompt = captor.getValue().replaceAll("\\s+", " ");

        assertTrue(prompt.contains(
                "ASSISTANT: Could you please tell me which year of your course you are currently in?"));
        assertTrue(prompt.contains(
                "by providing profile or eligibility details"));
        assertEquals(IntentType.SCHOLARSHIP, response.getType());
    }

    @Test
    void promptGivesPrecedenceToSelfContainedGeneralKnowledgeQuestions() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"type\":\"GENERAL_CHAT\"}");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify(
                "what is social category",
                List.of(new ChatMessage(
                        MessageRole.ASSISTANT,
                        "Could you please tell me your social category?",
                        LocalDateTime.now())));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(captor.capture());
        String prompt = captor.getValue().replaceAll("\\s+", " ");

        assertTrue(prompt.contains(
                "does not ask about a scholarship scheme or its facts, output GENERAL_CHAT"));
        assertTrue(prompt.contains("\"what is a social category?\""));
        assertEquals(IntentType.GENERAL_CHAT, response.getType());
    }

    @Test
    void returnsUnknownWhenModelResponseCannotBeParsed() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("not json");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify("hello", List.of());

        assertEquals(IntentType.UNKNOWN, response.getType());
    }

    @Test
    void returnsUnknownWhenModelResponseOmitsTheType() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"informational\":true}");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify("hello", List.of());

        assertEquals(IntentType.UNKNOWN, response.getType());
    }

    @Test
    void promptTeachesTheInformationalFlagAndAcceptsItFromTheModel() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"type\":\"SCHOLARSHIP\",\"informational\":true}");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify(
                "What is the family income limit for Top Class SC?",
                List.of());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(captor.capture());
        String prompt = captor.getValue().replaceAll("\\s+", " ");

        assertTrue(prompt.contains("{\"type\": \"SCHOLARSHIP\", \"informational\": false}"));
        assertTrue(prompt.contains("What is the family income limit for Top Class SC?"));
        assertTrue(prompt.contains("When in doubt about \"informational\", output false"));
        assertEquals(IntentType.SCHOLARSHIP, response.getType());
        assertTrue(response.isInformational());
    }

    @Test
    void aResponseWithoutTheInformationalKeyStaysOnTheAssessmentPath() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"type\":\"SCHOLARSHIP\"}");

        IntentClassification classifier = new IntentClassification(chatModel, objectMapper);
        IntentResponse response = classifier.classify(
                "Am I eligible for Top Class SC?",
                List.of());

        assertEquals(IntentType.SCHOLARSHIP, response.getType());
        assertFalse(response.isInformational());
    }
}