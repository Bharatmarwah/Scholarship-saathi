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
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Which schemes do you have?" is a question about the application, not about
 * a fact in a scheme guideline. It must be answered from the registry before
 * any model call, because retrieval can only return passages that exist in the
 * corpus and no guideline contains the catalogue.
 */
class ChatOrchestratorSchemeCatalogTest {

    private static final String QUERY = "which schemes do you have?";
    private static final String CATALOG_ANSWER = "1. **PM-USP CSSS** ... 5. **Top Class Education - PwD** ...";
    private static final String GUIDELINES_QUERY = "Where are Scheme Guidelines available?";
    private static final String GUIDELINES_ANSWER =
            "Scheme guidelines are published by the administering department or ministry.";

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
    private final PendingValidationState pendingValidationState = mock(PendingValidationState.class);
    private final PendingAnswerResolver pendingAnswerResolver = mock(PendingAnswerResolver.class);
    private final ValidationEngine validationEngine = mock(ValidationEngine.class);
    private final ScholarshipRetrieval scholarshipRetrieval = mock(ScholarshipRetrieval.class);
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator =
            mock(ScholarshipAnswerGenerator.class);
    private final EligibilityEngine eligibilityEngine = mock(EligibilityEngine.class);
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter =
            mock(EligibilityAnswerFormatter.class);
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
    void catalogueQuestionIsAnsweredWithoutIntentRetrievalOrValidation() {
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(QUERY);
        when(schemeCatalogResponder.isCatalogQuery(QUERY)).thenReturn(true);
        when(schemeCatalogResponder.answer()).thenReturn(CATALOG_ANSWER);

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(QUERY);

        ChatResponse result = orchestrator.processChat(request);

        assertNotNull(result);
        assertEquals(CATALOG_ANSWER, result.getResponse());
        assertEquals(CATALOG_ANSWER, result.getConversationMemorySummary());

        verify(intentClassification, never()).classify(anyString(), anyList());
        verify(queryComposer, never()).compose(anyString(), anyList(), anyList());
        verify(validationEngine, never()).validateAll(any(), anyList(), anyString());
        verify(validationEngine, never()).resolveScope(anyList(), anyString());
        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
        verify(scholarshipAnswerGenerator, never()).answer(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
        verify(semanticMemoryManager, never()).addSemanticMemory(any());

        verify(conversationMemoryManager).addUserMessage(request);
        verify(conversationMemoryManager).addAssistantMessage(eq("c1"), eq(result));
    }

    @Test
    void genericGuidelinesLocationQuestionIsAnsweredWithoutRetrieval() {
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(GUIDELINES_QUERY);
        when(schemeCatalogResponder.isGuidelinesLocationQuery(GUIDELINES_QUERY))
                .thenReturn(true);
        when(schemeCatalogResponder.guidelinesLocationAnswer())
                .thenReturn(GUIDELINES_ANSWER);

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(GUIDELINES_QUERY);

        ChatResponse result = orchestrator.processChat(request);

        assertNotNull(result);
        assertEquals(GUIDELINES_ANSWER, result.getResponse());
        assertEquals(GUIDELINES_ANSWER, result.getConversationMemorySummary());

        verify(schemeCatalogResponder).isCatalogQuery(GUIDELINES_QUERY);
        verify(schemeCatalogResponder).isGuidelinesLocationQuery(GUIDELINES_QUERY);
        verify(intentClassification, never()).classify(anyString(), anyList());
        verify(queryComposer, never()).compose(anyString(), anyList(), anyList());
        verify(validationEngine, never()).validateAll(any(), anyList(), anyString());
        verify(validationEngine, never()).resolveScope(anyList(), anyString());
        verify(scholarshipRetrieval, never()).search(anyString(), anyList());
        verify(scholarshipAnswerGenerator, never()).answer(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
        verify(semanticMemoryManager, never()).addSemanticMemory(any());

        verify(conversationMemoryManager).addUserMessage(request);
        verify(conversationMemoryManager).addAssistantMessage(eq("c1"), eq(result));
    }
}
