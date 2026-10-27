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
import com.bharat.scholarship_rag_backend.retrieval.ReformulatedQuery;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipAnswerGenerator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipRetrieval;
import com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import com.bharat.scholarship_rag_backend.service.StudentProfileService;
import com.bharat.scholarship_rag_backend.validator.PendingAnswerResolver;
import com.bharat.scholarship_rag_backend.validator.PendingValidationState;
import com.bharat.scholarship_rag_backend.validator.QuestionBuilder;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Informational questions used to be run through {@code validateAll}, which
 * stored the fields the validator would have asked. A factual question then
 * left stale pending state behind, so the next turn merged an unrelated answer
 * and the resolver could fill the wrong field. The scope must be resolved
 * without validation, and a factual digression must not cancel an eligibility
 * flow already in progress.
 */
class ChatOrchestratorInformationalScopeTest {

    private final StreamingChatModel streamingModel = mock(StreamingChatModel.class);
    private final ConversationMemory conversationMemory = mock(ConversationMemory.class);
    private final ConversationMemoryManager conversationMemoryManager = mock(ConversationMemoryManager.class);
    private final QueryGate queryGate = mock(QueryGate.class);
    private final QueryEnricher queryEnricher = mock(QueryEnricher.class);
    private final IntentClassification intentClassification = mock(IntentClassification.class);
    private final SemanticMemoryManager semanticMemoryManager = mock(SemanticMemoryManager.class);
    private final QueryEmbedding queryEmbedding = mock(QueryEmbedding.class);
    private final QueryComposer queryComposer = mock(QueryComposer.class);
    private final RetrievalQueryReformulator retrievalQueryReformulator =
            mock(RetrievalQueryReformulator.class);
    private final StudentProfileService studentProfileService = mock(StudentProfileService.class);
    private final ProfileValueNormalizer profileValueNormalizer = mock(ProfileValueNormalizer.class);
    private final PendingAnswerResolver pendingAnswerResolver = mock(PendingAnswerResolver.class);
    private final ScholarshipRetrieval scholarshipRetrieval = mock(ScholarshipRetrieval.class);
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator =
            mock(ScholarshipAnswerGenerator.class);
    private final EligibilityEngine eligibilityEngine = mock(EligibilityEngine.class);
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter =
            mock(EligibilityAnswerFormatter.class);
    private final SchemeCatalogResponder schemeCatalogResponder = mock(SchemeCatalogResponder.class);

    // One real pending-state instance shared by the validator and the
    // orchestrator, so a write would be visible to the test.
    private final PendingValidationState pendingValidationState = new PendingValidationState();
    private final ValidationEngine validationEngine = spy(new ValidationEngine(
            new SchemeRegistry(),
            new ProfileFieldAccess(),
            new QuestionBuilder(),
            pendingValidationState));

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
            profileValueNormalizer,
            pendingValidationState,
            pendingAnswerResolver,
            validationEngine,
            scholarshipRetrieval,
            scholarshipAnswerGenerator,
            eligibilityEngine,
            eligibilityAnswerFormatter,
            schemeCatalogResponder);

    @Test
    void factualQuestionUsesScopeOnlyAndLeavesNoPendingState() {
        String query = "What documents are required for Top Class SC?";
        stubInformationalTurn(query, "Top Class SC", SchemeType.TOP_CLASS_SC);

        orchestrator.processChat(request(query));

        verify(validationEngine).resolveScope(anyList(), eq("c1"));
        verify(validationEngine, never()).validateAll(any(), anyList(), anyString());
        assertTrue(pendingValidationState.askedFields("c1").isEmpty(),
                "an informational question must not record asked fields");
        assertFalse(pendingValidationState.hasPending("c1"),
                "an informational question must not start a validation flow");
    }

    @Test
    void factualDigressionDoesNotCancelAnEligibilityFlowInProgress() {
        pendingValidationState.store("c1", SchemeType.TOP_CLASS_SC,
                List.of(StudentProfileField.EDUCATION_LEVEL));

        String query = "What documents are required for Ishan Uday?";
        stubInformationalTurn(query, "Ishan Uday", SchemeType.ISHAN_UDAY);

        orchestrator.processChat(request(query));

        verify(validationEngine, never()).validateAll(any(), anyList(), anyString());
        assertTrue(pendingValidationState.hasPending("c1"),
                "the eligibility flow must survive a factual digression");
        assertEquals(List.of(SchemeType.TOP_CLASS_SC),
                pendingValidationState.targetSchemes("c1"));
        assertEquals(List.of(StudentProfileField.EDUCATION_LEVEL),
                pendingValidationState.askedFields("c1"));
    }

    @Test
    void plainAnswerToDiscoveryQuestionContinuesValidation() {
        pendingValidationState.store("c1", List.of(),
                List.of(StudentProfileField.EDUCATION_LEVEL));

        String query = "12th";
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.SCHOLARSHIP));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of(), Map.of()));

        when(studentProfileService.getOrCreate(anyString()))
                .thenReturn(StudentProfileDto.builder().conversationId("c1").build());
        when(studentProfileService.applyExtracted(anyString(), anyMap()))
                .thenReturn(StudentProfileDto.builder()
                        .conversationId("c1")
                        .educationLevel("CLASS_12")
                        .build());

        orchestrator.processChat(request(query));

        verify(validationEngine).validateAll(any(), anyList(), eq("c1"));
        verify(validationEngine, never()).resolveScope(anyList(), anyString());
        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
        assertTrue(pendingValidationState.hasAskedFields("c1"),
                "the discovery flow must ask for the next field instead of stalling");
    }

    private void stubInformationalTurn(String query, String targetScheme, SchemeType scheme) {
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.of(IntentType.SCHOLARSHIP, true));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of(targetScheme), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);

        when(retrievalQueryReformulator.reformulate(anyString(), anyList(), any(), any()))
                .thenReturn(new ReformulatedQuery(query, scheme, List.of(scheme)));
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());

        ChatResponse grounded = new ChatResponse();
        grounded.setResponse("answer");
        when(scholarshipAnswerGenerator.answer(anyString(), anyList())).thenReturn(grounded);
    }

    private static ChatRequest request(String query) {
        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);
        return request;
    }
}
