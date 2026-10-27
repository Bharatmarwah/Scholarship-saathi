package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.catalog.SchemeCatalogResponder;
import com.bharat.scholarship_rag_backend.composer.QueryComposer;
import com.bharat.scholarship_rag_backend.composer.QueryComposerResult;
import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityAnswerFormatter;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityEngine;
import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.enrichment.QueryEnricher;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.intent.IntentClassification;
import com.bharat.scholarship_rag_backend.intent.IntentResponse;
import com.bharat.scholarship_rag_backend.intent.IntentType;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemoryManager;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryManager;
import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipAnswerGenerator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipRetrieval;
import com.bharat.scholarship_rag_backend.service.StudentProfileService;
import com.bharat.scholarship_rag_backend.validator.PendingAnswerResolver;
import com.bharat.scholarship_rag_backend.validator.PendingValidationState;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import com.bharat.scholarship_rag_backend.validator.ValidationMode;
import com.bharat.scholarship_rag_backend.validator.ValidationResult;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "I'm in 12th" is a profile fact, not an eligibility assessment. It must reach
 * the profile whether the intent model calls it GENERAL_CHAT or SCHOLARSHIP,
 * and it must not start the validation flow or search the corpus.
 */
class ChatOrchestratorProfileStatementTest {

    private final StreamingChatModel streamingModel = mock(StreamingChatModel.class);
    private final ConversationMemory conversationMemory = mock(ConversationMemory.class);
    private final ConversationMemoryManager conversationMemoryManager = mock(ConversationMemoryManager.class);
    private final QueryGate queryGate = mock(QueryGate.class);
    private final QueryEnricher queryEnricher = mock(QueryEnricher.class);
    private final IntentClassification intentClassification = mock(IntentClassification.class);
    private final SemanticMemoryManager semanticMemoryManager = mock(SemanticMemoryManager.class);
    private final QueryEmbedding queryEmbedding = mock(QueryEmbedding.class);
    private final QueryComposer queryComposer = mock(QueryComposer.class);
    private final RetrievalQueryReformulator retrievalQueryReformulator = mock(RetrievalQueryReformulator.class);
    private final StudentProfileService studentProfileService = mock(StudentProfileService.class);
    private final PendingValidationState pendingValidationState = mock(PendingValidationState.class);
    private final PendingAnswerResolver pendingAnswerResolver = mock(PendingAnswerResolver.class);
    private final ValidationEngine validationEngine = mock(ValidationEngine.class);
    private final ScholarshipRetrieval scholarshipRetrieval = mock(ScholarshipRetrieval.class);
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator = mock(ScholarshipAnswerGenerator.class);
    private final EligibilityEngine eligibilityEngine = mock(EligibilityEngine.class);
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter = mock(EligibilityAnswerFormatter.class);
    private final SchemeCatalogResponder schemeCatalogResponder = mock(SchemeCatalogResponder.class);

    private final ChatOrchestrator orchestrator = new ChatOrchestrator(
            streamingModel,
            conversationMemory,
            conversationMemoryManager,
            queryGate,
            queryEnricher,
            intentClassification,
            semanticMemoryManager,
            queryEmbedding,
            queryComposer,
            retrievalQueryReformulator,
            studentProfileService,
            new ProfileValueNormalizer(),
            pendingValidationState,
            pendingAnswerResolver,
            validationEngine,
            scholarshipRetrieval,
            scholarshipAnswerGenerator,
            eligibilityEngine,
            eligibilityAnswerFormatter,
            schemeCatalogResponder);

    @Test
    void generalChatSelfStatementUpdatesTheProfileWithoutValidation() {
        String query = "I'm in 12th";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.GENERAL_CHAT));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);

        ChatResponse result = orchestrator.processChat(request(query));

        Map<String, Object> applied = capturedAppliedValues();
        assertEquals("CLASS_12", applied.get("EDUCATION_LEVEL"));
        assertEquals(12, applied.get("CURRENT_CLASS"));

        assertNotNull(result);
        assertTrue(result.getResponse().toLowerCase().contains("saved"),
                "a profile statement should be acknowledged, got: " + result.getResponse());
        assertTrue(result.getResponse().contains("education level and current class"),
                "the acknowledgement should read naturally, got: " + result.getResponse());

        verify(queryComposer, never()).compose(anyString(), anyList(), anyList());
        verify(validationEngine, never()).validateAll(any(), anyList(), anyString());
        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
    }

    @Test
    void scholarshipIntentProfileStatementUpdatesTheProfileWithoutValidation() {
        String query = "I'm in 12th";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.SCHOLARSHIP));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any()))
                .thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of(), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);

        ChatResponse result = orchestrator.processChat(request(query));

        Map<String, Object> applied = capturedAppliedValues();
        assertEquals("CLASS_12", applied.get("EDUCATION_LEVEL"));
        assertEquals(12, applied.get("CURRENT_CLASS"));

        assertNotNull(result);
        assertTrue(result.getResponse().toLowerCase().contains("saved"),
                "a profile statement should be acknowledged, got: " + result.getResponse());

        verify(validationEngine, never()).validateAll(any(), anyList(), anyString());
        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
    }

    @Test
    void fullProfileMessageIsAppliedBeforeValidationAsksAnything() {
        String query = "I am an Indian student from Assam, currently in the first year of a "
                + "regular/full-time B.Sc undergraduate course. My annual family income is "
                + "\u20b93,00,000. I have not completed any undergraduate degree before and "
                + "this is a fresh application. Am I eligible for which scholarship?";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.SCHOLARSHIP));
        when(queryEmbedding.embedQuery(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any()))
                .thenReturn(List.of());
        when(pendingValidationState.askedFields(anyString())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of(), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);
        when(validationEngine.validateAll(any(), anyList(), anyString()))
                .thenReturn(ValidationResult.missing(
                        ValidationMode.SCHOLARSHIP_DISCOVERY,
                        null,
                        List.of(),
                        List.of(StudentProfileField.SOCIAL_CATEGORY),
                        "What is your social category?",
                        null));

        ChatResponse result = orchestrator.processChat(request(query));

        Map<String, Object> applied = capturedAppliedValues();
        assertEquals(1, applied.get("CURRENT_YEAR"));
        assertEquals(0, new BigDecimal("300000")
                .compareTo((BigDecimal) applied.get("ANNUAL_FAMILY_INCOME")));
        assertEquals("Assam", applied.get("DOMICILE_STATE"));
        assertEquals("BSc", applied.get("COURSE"));
        assertEquals("UG", applied.get("EDUCATION_LEVEL"));
        assertEquals(Boolean.TRUE, applied.get("REGULAR_MODE"));
        assertEquals(Boolean.FALSE, applied.get("HAS_PRIOR_DEGREE"));
        assertEquals("FRESH", applied.get("APPLICATION_TYPE"));
        assertEquals("Indian", applied.get("NATIONALITY"));
        assertEquals("What is your social category?", result.getResponse());
    }

    private static ChatRequest request(String query) {
        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedAppliedValues() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(studentProfileService).applyExtracted(eq("c1"), captor.capture());
        return captor.getValue();
    }
}
