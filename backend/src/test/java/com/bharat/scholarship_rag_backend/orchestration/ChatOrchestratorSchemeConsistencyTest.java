package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.catalog.SchemeCatalogResponder;
import com.bharat.scholarship_rag_backend.composer.QueryComposer;
import com.bharat.scholarship_rag_backend.composer.QueryComposerResult;
import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.request.MessageRole;
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
import com.bharat.scholarship_rag_backend.service.StudentProfileService;
import com.bharat.scholarship_rag_backend.validator.PendingAnswerResolver;
import com.bharat.scholarship_rag_backend.validator.PendingValidationState;
import com.bharat.scholarship_rag_backend.validator.QuestionBuilder;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import com.bharat.scholarship_rag_backend.validator.ValidationResult;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for scope drifting across turns. The composer sees the
 * whole conversation history, so after an earlier PM-USP CSSS question it can
 * return that scheme for a new, self-contained Top Class SC question. A scheme
 * explicitly named in the current query must win, and a self-contained generic
 * question must not inherit a scheme from history at all.
 */
class ChatOrchestratorSchemeConsistencyTest {

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

    private final SchemeRegistry registry = new SchemeRegistry();
    private final PendingValidationState pendingValidationState = new PendingValidationState();
    private final ValidationEngine validationEngine = new ValidationEngine(
            registry,
            new ProfileFieldAccess(),
            new QuestionBuilder(),
            pendingValidationState);

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
    void selfContainedFactualQuestionKeepsItsExplicitSchemeDespiteStaleComposerTarget() {
        String query = "what are the administractive expenses for top class sc ?";

        stubTurn(query,
                "PM-USP CSSS",
                "Ignore the previous question. What are the administrative expenses for Top Class SC?");

        orchestrator.processChat(request(query));

        ArgumentCaptor<ValidationResult> validation =
                ArgumentCaptor.forClass(ValidationResult.class);
        verify(retrievalQueryReformulator).reformulate(
                eq(query), anyList(), any(), validation.capture());
        assertEquals(List.of(SchemeType.TOP_CLASS_SC),
                validation.getValue().getTargetSchemes());

        verify(scholarshipAnswerGenerator).answer(eq(query), anyList());
        verify(eligibilityEngine, org.mockito.Mockito.never()).assessAll(any(), anyList());
    }

    @Test
    void selfContainedGenericQuestionDoesNotInheritASchemeFromHistory() {
        String query = "Where are Scheme Guidelines available?";

        stubTurn(query,
                "PM-USP CSSS",
                "Where are the PM-USP CSSS scheme guidelines available?");

        orchestrator.processChat(request(query));

        ArgumentCaptor<ValidationResult> validation =
                ArgumentCaptor.forClass(ValidationResult.class);
        verify(retrievalQueryReformulator).reformulate(
                eq(query), anyList(), any(), validation.capture());
        assertEquals(registry.allSchemes(), validation.getValue().getTargetSchemes());

        verify(scholarshipAnswerGenerator).answer(eq(query), anyList());
    }

    @Test
    void explicitSchemeWinsOverComposerTargetEvenWhenContextIsTrusted() {
        String query = "what is the income limit for top class sc?";

        stubTurn(query,
                "PM-USP CSSS",
                "What is the income limit for Top Class SC after PM-USP CSSS?",
                true);

        orchestrator.processChat(request(query));

        ArgumentCaptor<ValidationResult> validation =
                ArgumentCaptor.forClass(ValidationResult.class);
        verify(retrievalQueryReformulator).reformulate(
                eq("What is the income limit for Top Class SC after PM-USP CSSS?"),
                anyList(),
                any(),
                validation.capture());
        assertEquals(List.of(SchemeType.TOP_CLASS_SC),
                validation.getValue().getTargetSchemes());
    }

    private void stubTurn(String query, String staleComposerScheme, String composerQuery) {
        stubTurn(query, staleComposerScheme, composerQuery, false);
    }

    private void stubTurn(
            String query,
            String staleComposerScheme,
            String composerQuery,
            boolean needsContext) {
        when(conversationMemory.allRecentConversation(anyString()))
                .thenReturn(List.of(
                        new ChatMessage(MessageRole.USER, "what is pm css usp?", null),
                        new ChatMessage(MessageRole.ASSISTANT,
                                "PM-USP CSSS is the central sector scholarship scheme.", null)));
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(needsContext);
        when(queryEnricher.enrich(anyString(), anyList())).thenReturn(query);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.of(IntentType.SCHOLARSHIP, true));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(
                        composerQuery, List.of(staleComposerScheme), Map.of()));

        StudentProfileDto profile = StudentProfileDto.builder().conversationId("c1").build();
        when(studentProfileService.getOrCreate(anyString())).thenReturn(profile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(profile);

        when(retrievalQueryReformulator.reformulate(anyString(), anyList(), any(), any()))
                .thenReturn(new ReformulatedQuery(
                        query, null, registry.allSchemes()));
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());

        ChatResponse grounded = new ChatResponse();
        grounded.setResponse("grounded answer");
        when(scholarshipAnswerGenerator.answer(anyString(), anyList())).thenReturn(grounded);
    }

    private static ChatRequest request(String query) {
        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);
        return request;
    }
}
