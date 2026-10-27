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
import com.bharat.scholarship_rag_backend.validator.QuestionBuilder;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the bug where an empty student profile asking
 * "which scholarship am I eligible for" bypassed validation (because the query
 * looked like a question), searched an empty knowledge base and answered
 * "could not find that information".
 *
 * <p>Validation must never be suppressed: with no profile the orchestrator must
 * ask for the missing details and must NOT reach retrieval or eligibility.
 */
class ChatOrchestratorValidationNotSuppressedTest {

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
    private final ProfileValueNormalizer profileValueNormalizer = mock(ProfileValueNormalizer.class);
    private final PendingValidationState pendingValidationState = mock(PendingValidationState.class);
    private final PendingAnswerResolver pendingAnswerResolver = mock(PendingAnswerResolver.class);
    private final ScholarshipRetrieval scholarshipRetrieval = mock(ScholarshipRetrieval.class);
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator = mock(ScholarshipAnswerGenerator.class);
    private final EligibilityEngine eligibilityEngine = mock(EligibilityEngine.class);
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter = mock(EligibilityAnswerFormatter.class);
    private final SchemeCatalogResponder schemeCatalogResponder = mock(SchemeCatalogResponder.class);

    // The real validator, so the suppression behaviour is exercised end to end.
    private final ValidationEngine validationEngine = new ValidationEngine(
            new SchemeRegistry(),
            new ProfileFieldAccess(),
            new QuestionBuilder(),
            new PendingValidationState());

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
    void emptyProfileQuestionMustBeAskedNotAnsweredFromEmptyKnowledgeBase() {
        String query = "which scholarship am i eligible for";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.SCHOLARSHIP));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of(), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);

        ChatResponse result = orchestrator.processChat(request);

        assertNotNull(result, "orchestrator must not return null");
        assertNotNull(result.getResponse(), "a validation question must be returned");
        assertTrue(result.getResponse().toLowerCase().contains("level"),
                "expected a question about the student's level, got: " + result.getResponse());
        assertFalse(result.getResponse().toLowerCase().contains("could not find"),
                "must not fall through to the empty-knowledge-base reply");

        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
    }
}
