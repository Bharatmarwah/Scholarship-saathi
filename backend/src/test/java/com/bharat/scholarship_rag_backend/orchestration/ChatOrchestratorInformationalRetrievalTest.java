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
import com.bharat.scholarship_rag_backend.retrieval.GenerationUnavailableException;
import com.bharat.scholarship_rag_backend.retrieval.ReformulatedQuery;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalUnavailableException;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipAnswerGenerator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipRetrieval;
import com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
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
 * Regression guard for the routing bug where every scholarship query, factual
 * or not, ended up in the profile-collection flow: "What is the income limit
 * for Top Class SC?" came back as a request for the student's education level
 * because the validator had no profile to score, and the retrieval branch was
 * unreachable.
 *
 * <p>An informational query must still resolve its target scheme, but it must
 * ignore the missing-field gate and answer from the knowledge base.
 */
class ChatOrchestratorInformationalRetrievalTest {

    private static final String GROUNDED_ANSWER =
            "The annual family income must be up to Rs. 8.00 lakh.";

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
    private final ScholarshipRetrieval scholarshipRetrieval = mock(ScholarshipRetrieval.class);
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator =
            mock(ScholarshipAnswerGenerator.class);
    private final EligibilityEngine eligibilityEngine = mock(EligibilityEngine.class);
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter =
            mock(EligibilityAnswerFormatter.class);
    private final SchemeCatalogResponder schemeCatalogResponder = mock(SchemeCatalogResponder.class);

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
    void factualQueryWithEmptyProfileGoesToRetrievalNotProfileQuestions() {
        String query = "What is the family income limit for Top Class SC?";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.of(IntentType.SCHOLARSHIP, true));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of("Top Class SC"), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);

        when(retrievalQueryReformulator.reformulate(anyString(), anyList(), any(), any()))
                .thenReturn(new ReformulatedQuery(
                        query, SchemeType.TOP_CLASS_SC, List.of(SchemeType.TOP_CLASS_SC)));
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());

        ChatResponse grounded = new ChatResponse();
        grounded.setResponse(GROUNDED_ANSWER);
        when(scholarshipAnswerGenerator.answer(anyString(), anyList())).thenReturn(grounded);

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);

        ChatResponse result = orchestrator.processChat(request);

        assertNotNull(result, "orchestrator must not return null");
        assertEquals(GROUNDED_ANSWER, result.getResponse());

        verify(retrievalQueryReformulator).reformulate(anyString(), anyList(), any(), any());
        verify(scholarshipRetrieval).search(anyString(), anyList());
        verify(scholarshipAnswerGenerator).answer(eq(query), anyList());

        verify(eligibilityEngine, never()).assessAll(any(), anyList());
        verify(conversationMemoryManager).addAssistantMessage(anyString(), any());
    }

    @Test
    void canIGetAnApplicationFormIsNotASelfAssessmentRequest() {
        String query = "Can I get a scholarship application form online?";
        stubFactualQuery(query);
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());
        when(scholarshipAnswerGenerator.answer(anyString(), anyList()))
                .thenReturn(new ChatResponse(GROUNDED_ANSWER, null, null));

        ChatResponse result = orchestrator.processChat(request(query));

        assertEquals(GROUNDED_ANSWER, result.getResponse());
        verify(scholarshipRetrieval).search(anyString(), anyList());
        verify(scholarshipAnswerGenerator).answer(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
    }

    @Test
    void whereToGetTheApplicationFormIsNotASelfAssessmentRequest() {
        String query = "Where can I get a scholarship application form?";

        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.of(IntentType.SCHOLARSHIP, true));
        when(queryEmbedding.embedQuery(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of(), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);
        when(retrievalQueryReformulator.reformulate(anyString(), anyList(), any(), any()))
                .thenReturn(ReformulatedQuery.unscoped(query));
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());
        when(scholarshipAnswerGenerator.answer(anyString(), anyList()))
                .thenReturn(new ChatResponse(GROUNDED_ANSWER, null, null));

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);

        ChatResponse result = orchestrator.processChat(request);

        assertEquals(GROUNDED_ANSWER, result.getResponse());
        verify(scholarshipRetrieval).search(anyString(), anyList());
        verify(eligibilityEngine, never()).assessAll(any(), anyList());
    }

    @Test
    void retrievalOutageIsReportedAsTemporaryNotMissingInformation() {
        String query = "What is the family income limit for Top Class SC?";
        stubFactualQuery(query);
        when(scholarshipRetrieval.search(anyString(), anyList()))
                .thenThrow(new RetrievalUnavailableException("both arms down"));

        ChatResponse result = orchestrator.processChat(request(query));

        assertNotNull(result);
        assertTrue(result.getResponse().contains("temporarily"));
        assertFalse(result.getResponse().contains("could not find"),
                "an outage must not be reported as missing evidence");
        verify(scholarshipAnswerGenerator, never()).answer(anyString(), anyList());
        verify(semanticMemoryManager, never()).addSemanticMemory(any());
        verify(conversationMemoryManager).addAssistantMessage(anyString(), any());
    }

    @Test
    void generationOutageIsReportedAsTemporaryNotMissingInformation() {
        String query = "What is the family income limit for Top Class SC?";
        stubFactualQuery(query);
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());
        when(scholarshipAnswerGenerator.answer(anyString(), anyList()))
                .thenThrow(new GenerationUnavailableException(
                        "provider down", new RuntimeException("provider down")));

        ChatResponse result = orchestrator.processChat(request(query));

        assertNotNull(result);
        assertTrue(result.getResponse().contains("temporarily"));
        assertFalse(result.getResponse().contains("could not find"),
                "an outage must not be reported as missing evidence");
        verify(semanticMemoryManager, never()).addSemanticMemory(any());
        verify(conversationMemoryManager).addAssistantMessage(anyString(), any());
    }

    private void stubFactualQuery(String query) {
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.of(IntentType.SCHOLARSHIP, true));
        when(queryEmbedding.embedQuery(anyString())).thenReturn(new float[384]);
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of("Top Class SC"), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);
        when(retrievalQueryReformulator.reformulate(anyString(), anyList(), any(), any()))
                .thenReturn(new ReformulatedQuery(
                        query, SchemeType.TOP_CLASS_SC, List.of(SchemeType.TOP_CLASS_SC)));
    }

    private static ChatRequest request(String query) {
        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);
        return request;
    }
}
