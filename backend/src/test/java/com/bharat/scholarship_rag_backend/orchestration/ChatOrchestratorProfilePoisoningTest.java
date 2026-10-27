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
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.service.StudentProfileService;
import com.bharat.scholarship_rag_backend.validator.PendingAnswerResolver;
import com.bharat.scholarship_rag_backend.validator.PendingValidationState;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for profile poisoning: the deterministic category/course
 * detectors match bare tokens such as "sc" or "msc" anywhere in the message, so
 * a question like "what scholarships are there for SC students?" used to
 * overwrite a stored GENERAL category with SC and flip the eligibility verdict.
 *
 * <p>Detectors must only backfill a field the profile does not hold yet.
 */
class ChatOrchestratorProfilePoisoningTest {

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
    private final PendingValidationState pendingValidationState = mock(PendingValidationState.class);
    private final PendingAnswerResolver pendingAnswerResolver = mock(PendingAnswerResolver.class);
    private final ScholarshipRetrieval scholarshipRetrieval = mock(ScholarshipRetrieval.class);
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator =
            mock(ScholarshipAnswerGenerator.class);
    private final EligibilityEngine eligibilityEngine = mock(EligibilityEngine.class);
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter =
            mock(EligibilityAnswerFormatter.class);
    private final SchemeCatalogResponder schemeCatalogResponder = mock(SchemeCatalogResponder.class);

    // The real normalizer, so the token detectors that caused the poisoning are
    // exercised end to end rather than mocked away.
    private final ProfileValueNormalizer profileValueNormalizer = new ProfileValueNormalizer();

    private final ValidationEngine validationEngine = new ValidationEngine(
            new com.bharat.scholarship_rag_backend.scheme.SchemeRegistry(),
            new com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess(),
            new com.bharat.scholarship_rag_backend.validator.QuestionBuilder(),
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
    void aQuestionMentioningAnotherCategoryMustNotOverwriteTheStoredOne() {
        Map<String, Object> applied = runAndCaptureAppliedValues(
                "What scholarships are there for SC students?",
                StudentProfileDto.builder()
                        .conversationId("c1")
                        .socialCategory("GENERAL")
                        .build());

        assertFalse(applied.containsKey("SOCIAL_CATEGORY"),
                "a question about SC students must not change the stored category, got: " + applied);
    }

    @Test
    void theDetectorMustNotBackfillACategoryFromAQuestionAboutOtherStudents() {
        Map<String, Object> applied = runAndCaptureAppliedValues(
                "What scholarships are there for SC students?",
                StudentProfileDto.builder().conversationId("c1").build());

        assertFalse(applied.containsKey("SOCIAL_CATEGORY"),
                "a question about SC students must not become the user's own category, got: "
                        + applied);
    }

    @Test
    void aQuestionMentioningAnotherCourseMustNotOverwriteTheStoredOne() {
        Map<String, Object> applied = runAndCaptureAppliedValues(
                "what about msc?",
                StudentProfileDto.builder()
                        .conversationId("c1")
                        .course("B.Tech")
                        .build(),
                Map.of());

        assertFalse(applied.containsKey("COURSE"),
                "a question mentioning another course must not change the stored course, got: " + applied);
    }

    @Test
    void aComposerExtractionMustNotOverwriteTheStoredCategory() {
        Map<String, Object> applied = runAndCaptureAppliedValues(
                "What scholarships are there for SC students?",
                StudentProfileDto.builder()
                        .conversationId("c1")
                        .socialCategory("GENERAL")
                        .build(),
                Map.of("SOCIAL_CATEGORY", "SC"));

        assertFalse(applied.containsKey("SOCIAL_CATEGORY"),
                "an extracted category from a question about SC students must not replace "
                        + "the stored GENERAL, got: " + applied);
    }

    @Test
    void aFirstPersonCorrectionStillReachesTheProfile() {
        Map<String, Object> applied = runAndCaptureAppliedValues(
                "I am OBC, am I eligible for PM YASASVI?",
                StudentProfileDto.builder()
                        .conversationId("c1")
                        .socialCategory("GENERAL")
                        .build(),
                Map.of("SOCIAL_CATEGORY", "OBC"));

        assertEquals("OBC", applied.get("SOCIAL_CATEGORY"),
                "a first-person correction must still be applied, got: " + applied);
    }

    private Map<String, Object> runAndCaptureAppliedValues(String query, StudentProfileDto storedProfile) {
        return runAndCaptureAppliedValues(query, storedProfile, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runAndCaptureAppliedValues(
            String query, StudentProfileDto storedProfile, Map<String, Object> extractedValues) {
        when(conversationMemory.allRecentConversation(anyString())).thenReturn(List.of());
        when(queryGate.clean(anyString())).thenReturn(query);
        when(queryGate.needsContext(anyString())).thenReturn(false);
        when(intentClassification.classify(anyString(), anyList()))
                .thenReturn(IntentResponse.of(IntentType.SCHOLARSHIP, true));
        when(queryEmbedding.embed(anyString())).thenReturn(new float[384]);
        when(semanticMemoryManager.listAllSemanticMemories(anyString(), any())).thenReturn(List.of());
        when(queryComposer.compose(anyString(), anyList(), anyList()))
                .thenReturn(new QueryComposerResult(query, List.of("Top Class SC"), extractedValues));
        when(studentProfileService.getOrCreate(anyString())).thenReturn(storedProfile);
        when(studentProfileService.applyExtracted(anyString(), anyMap())).thenReturn(storedProfile);
        when(retrievalQueryReformulator.reformulate(anyString(), anyList(), any(), any()))
                .thenReturn(new ReformulatedQuery(query, SchemeType.TOP_CLASS_SC, List.of(SchemeType.TOP_CLASS_SC)));
        when(scholarshipRetrieval.search(anyString(), anyList())).thenReturn(List.of());

        ChatResponse grounded = new ChatResponse();
        grounded.setResponse("answer");
        when(scholarshipAnswerGenerator.answer(anyString(), anyList())).thenReturn(grounded);

        ChatRequest request = new ChatRequest();
        request.setConversationId("c1");
        request.setQuery(query);
        orchestrator.processChat(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(studentProfileService).applyExtracted(eq("c1"), captor.capture());
        assertTrue(captor.getValue() != null, "extracted values must be passed to the profile");
        return captor.getValue();
    }
}
