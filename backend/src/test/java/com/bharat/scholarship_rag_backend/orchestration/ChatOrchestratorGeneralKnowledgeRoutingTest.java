package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.catalog.SchemeCatalogResponder;
import com.bharat.scholarship_rag_backend.composer.QueryComposer;
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
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A self-contained general-knowledge question ("what is social category") must
 * be routed to GENERAL_CHAT and answered directly, never pulled into the
 * scholarship profile-collection flow just because the history is scholarship.
 */
class ChatOrchestratorGeneralKnowledgeRoutingTest {

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
    void generalKnowledgeQuestionIsAnsweredDirectlyWithoutScholarshipFlow() {
        String query = "what is social category";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.GENERAL_CHAT));

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("A social category is a government classification of communities.");
            handler.onCompleteResponse(null);
            return null;
        }).when(streamingModel).chat(anyString(), any(StreamingChatResponseHandler.class));

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);

        ChatResponse result = orchestrator.processChat(request);

        assertNotNull(result);
        assertEquals(
                "A social category is a government classification of communities.",
                result.getResponse());

        verify(validationEngine, never()).validateAll(any(), anyList(), anyString());
        verify(queryComposer, never()).compose(anyString(), anyList(), anyList());
        verify(studentProfileService, never()).getOrCreate(anyString());
        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
    }

    @Test
    void intentIsClassifiedOnTheCleanedTurnNotTheEnrichedCombination() {
        String cleaned = "what about it";
        String combined = "what about the income limit for top class sc";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(cleaned);
        when(queryGate.needsContext(anyString())).thenReturn(true);
        when(queryEnricher.enrich(eq(cleaned), anyList())).thenReturn(combined);

        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.forType(IntentType.GENERAL_CHAT));

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("Sure.");
            handler.onCompleteResponse(null);
            return null;
        }).when(streamingModel).chat(anyString(), any(StreamingChatResponseHandler.class));

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(cleaned);

        ChatResponse result = orchestrator.processChat(request);

        assertNotNull(result);
        assertEquals("Sure.", result.getResponse());
        verify(intentClassification).classify(eq(cleaned), anyList());
    }
}
