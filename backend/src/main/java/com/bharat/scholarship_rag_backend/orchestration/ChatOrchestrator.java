package com.bharat.scholarship_rag_backend.orchestration;

import com.bharat.scholarship_rag_backend.catalog.SchemeCatalogResponder;
import com.bharat.scholarship_rag_backend.composer.QueryComposer;
import com.bharat.scholarship_rag_backend.composer.QueryComposerResult;
import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.dto.request.ChatMessage;
import com.bharat.scholarship_rag_backend.dto.request.ChatRequest;
import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityAnswerFormatter;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityEngine;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityReport;
import com.bharat.scholarship_rag_backend.enrichment.QueryEnricher;
import com.bharat.scholarship_rag_backend.enrichment.QueryGate;
import com.bharat.scholarship_rag_backend.intent.IntentClassification;
import com.bharat.scholarship_rag_backend.intent.IntentResponse;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemoryManager;
import com.bharat.scholarship_rag_backend.memory.conversation.ConversationMemory;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryManager;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryRequest;
import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryResponse;
import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.retrieval.GenerationUnavailableException;
import com.bharat.scholarship_rag_backend.retrieval.ReformulatedQuery;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalQueryReformulator;
import com.bharat.scholarship_rag_backend.retrieval.RetrievalUnavailableException;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipAnswerGenerator;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipChunkMatch;
import com.bharat.scholarship_rag_backend.retrieval.ScholarshipRetrieval;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import com.bharat.scholarship_rag_backend.service.StudentProfileService;
import com.bharat.scholarship_rag_backend.validator.PendingAnswerResolver;
import com.bharat.scholarship_rag_backend.validator.PendingValidationState;
import com.bharat.scholarship_rag_backend.validator.ValidationEngine;
import com.bharat.scholarship_rag_backend.validator.ValidationResult;
import com.bharat.scholarship_rag_backend.validator.ValidationStatus;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatOrchestrator {

    static final String TEMPORARY_UNAVAILABLE_ANSWER =
            "I'm temporarily unable to search or compose an answer from the scholarship "
                    + "documents. This is a service problem, not a statement that the "
                    + "information is missing. Please try again in a moment.";

    private final StreamingChatModel openAiStreamingChatModel;
    private final ConversationMemory conversationMemory;
    private final ConversationMemoryManager conversationMemoryManager;
    private final QueryGate queryGate;
    private final QueryEnricher queryEnricher;
    private final IntentClassification intentClassification;
    private final SemanticMemoryManager semanticMemoryManager;
    private final QueryEmbedding queryEmbedding;
    private final QueryComposer queryComposer;
    private final StudentProfileService studentProfileService;
    private final ProfileValueNormalizer profileValueNormalizer;
    private final PendingValidationState pendingValidationState;
    private final PendingAnswerResolver pendingAnswerResolver;
    private final RetrievalQueryReformulator retrievalQueryReformulator;
    private final ValidationEngine validationEngine;
    private final ScholarshipRetrieval scholarshipRetrieval;
    private final ScholarshipAnswerGenerator scholarshipAnswerGenerator;
    private final EligibilityEngine eligibilityEngine;
    private final EligibilityAnswerFormatter eligibilityAnswerFormatter;
    private final SchemeCatalogResponder schemeCatalogResponder;

    public ChatOrchestrator(
            StreamingChatModel openAiStreamingChatModel,
            ConversationMemory conversationMemory,
            ConversationMemoryManager conversationMemoryManager,
            QueryGate queryGate,
            QueryEnricher queryEnricher,
            IntentClassification intentClassification,
            SemanticMemoryManager semanticMemoryManager,
            QueryEmbedding queryEmbedding,
            QueryComposer queryComposer,
            RetrievalQueryReformulator retrievalQueryReformulator,
            StudentProfileService studentProfileService,
            ProfileValueNormalizer profileValueNormalizer,
            PendingValidationState pendingValidationState,
            PendingAnswerResolver pendingAnswerResolver,
            ValidationEngine validationEngine,
            ScholarshipRetrieval scholarshipRetrieval,
            ScholarshipAnswerGenerator scholarshipAnswerGenerator,
            EligibilityEngine eligibilityEngine,
            EligibilityAnswerFormatter eligibilityAnswerFormatter,
            SchemeCatalogResponder schemeCatalogResponder
    ) {
        this.openAiStreamingChatModel = openAiStreamingChatModel;
        this.conversationMemory = conversationMemory;
        this.conversationMemoryManager = conversationMemoryManager;
        this.queryGate = queryGate;
        this.queryEnricher = queryEnricher;
        this.intentClassification = intentClassification;
        this.semanticMemoryManager = semanticMemoryManager;
        this.queryEmbedding = queryEmbedding;
        this.queryComposer = queryComposer;
        this.retrievalQueryReformulator = retrievalQueryReformulator;
        this.studentProfileService = studentProfileService;
        this.profileValueNormalizer = profileValueNormalizer;
        this.pendingValidationState = pendingValidationState;
        this.pendingAnswerResolver = pendingAnswerResolver;
        this.validationEngine = validationEngine;
        this.scholarshipRetrieval = scholarshipRetrieval;
        this.scholarshipAnswerGenerator = scholarshipAnswerGenerator;
        this.eligibilityEngine = eligibilityEngine;
        this.eligibilityAnswerFormatter = eligibilityAnswerFormatter;
        this.schemeCatalogResponder = schemeCatalogResponder;
    }

    public ChatResponse processChat(ChatRequest chatRequest) {

        String conversationId = chatRequest.getConversationId();

        // Fetch recent conversation memory
        List<ChatMessage> recentConversationMessages =
                conversationMemory.allRecentConversation(conversationId);

        // Clean user query
        String cleanedQuery = queryGate.clean(chatRequest.getQuery());

        // Catalogue meta-questions are about the application, not a scheme
        // guideline. Answer them deterministically before any model call: a
        // guideline never contains this list, so retrieval would answer "not
        // found" even when the corpus is fully ingested.
        if (schemeCatalogResponder.isCatalogQuery(cleanedQuery)) {
            return catalogResponse(chatRequest, conversationId);
        }

        // "Where are the scheme guidelines available?" names no scheme. With
        // every scheme in scope, retrieval can pick one scheme's portal page
        // and look arbitrary, so answer the stable meta-fact deterministically.
        if (schemeCatalogResponder.isGuidelinesLocationQuery(cleanedQuery)) {
            return deterministicResponse(
                    chatRequest,
                    conversationId,
                    schemeCatalogResponder.guidelinesLocationAnswer());
        }

        /*
        INTENT CLASSIFICATION Classify the Raw Query
        you should not blend past conversation history with the new raw query when doing intent classification
         */
        IntentResponse intentResponse =
                intentClassification.classify(
                        cleanedQuery,
                        recentConversationMessages);

        log.info("Intent type {} informational {}", intentResponse.getType(),
                intentResponse.isInformational());

        // Enrich query using recent conversation context when required. This is
        // done after classification so the combined text is only used for
        // retrieval/composition, never to decide the intent.
        boolean needsContext = queryGate.needsContext(cleanedQuery);
        String enrichedQuery = needsContext
                ? queryEnricher.enrich(cleanedQuery, recentConversationMessages)
                : cleanedQuery;

        log.info("Processed query {}", enrichedQuery);

        // Response will be created by the corresponding intent flow
        ChatResponse chatResponse = null;

        switch (intentResponse.getType()) {

            case SCHOLARSHIP -> {

                // Add current user message to conversation memory
                conversationMemoryManager.addUserMessage(chatRequest);

                // Generate the search-side embedding. The bge model expects the
                // retrieval instruction on queries but not on stored text, so
                // this vector must not be reused as the document embedding.
                // Semantic memory is an enrichment: if the local model is
                // unavailable, eligibility and keyword retrieval must still run.
                List<SemanticMemoryResponse> semanticMemories;
                try {
                    float[] queryEmbeddings = queryEmbedding.embedQuery(enrichedQuery);
                    semanticMemories = semanticMemoryManager.listAllSemanticMemories(
                            conversationId,
                            queryEmbeddings
                    );
                } catch (RuntimeException e) {
                    log.warn("Failed to embed query for semantic memory; continuing without it", e);
                    semanticMemories = List.of();
                }

                // Skip/avoid unnecessary merge. If memories are present by chance
                // but completely irrelevant and there is no conversation history
                // and no pending validation to resolve, do not merge them in.
                boolean hasPendingValidation = pendingValidationState.hasPending(conversationId);
                boolean hasPendingDiscovery = !hasPendingValidation
                        && pendingValidationState.hasAskedFields(conversationId);
                boolean hasConversationContext = recentConversationMessages != null && !recentConversationMessages.isEmpty();
                boolean hasSemanticContext = semanticMemories != null && !semanticMemories.isEmpty();

                QueryComposerResult composerResult;
                if (hasPendingValidation) {
                    // Must merge to resolve follow-up answers correctly
                    composerResult = queryComposer.compose(enrichedQuery, recentConversationMessages, semanticMemories);
                } else if (hasConversationContext) {
                    // Has history - may need merging for context
                    composerResult = queryComposer.compose(enrichedQuery, recentConversationMessages, semanticMemories);
                } else if (hasSemanticContext) {
                    // Only memories, no history, no pending validation.
                    // Try to compose but instruct to avoid merging if irrelevant;
                    // composer will be conservative. Rare case as requested.
                    composerResult = queryComposer.compose(enrichedQuery, List.of(), semanticMemories);
                } else {
                    // Nothing to merge - use query directly for extraction/flow
                    composerResult = queryComposer.compose(enrichedQuery, List.of(), List.of());
                }

                List<SchemeType> explicitlyNamedSchemes =
                        validationEngine.schemesNamedIn(cleanedQuery);
                // The text detector is only one signal. The composer reports
                // whether it actually merged context, so a follow-up the
                // detector misses still uses the self-contained composed query
                // instead of the raw fragment.
                boolean contextTrusted = needsContext || composerResult.isUsedContext();
                List<String> effectiveTargetSchemes = effectiveTargetSchemes(
                        explicitlyNamedSchemes,
                        composerResult.getTargetSchemes(),
                        contextTrusted);
                String retrievalQuery = contextTrusted
                        ? composerResult.getCombinedQuery()
                        : enrichedQuery;

                log.info("Composed query {} composer schemes {} explicit schemes {} "
                                + "effective schemes {} needsContext {} composerUsedContext {}",
                        composerResult.getCombinedQuery(),
                        composerResult.getTargetSchemes(),
                        explicitlyNamedSchemes,
                        effectiveTargetSchemes,
                        needsContext,
                        composerResult.isUsedContext());

                //Get or create student profile
                StudentProfileDto profile =
                        studentProfileService
                                .getOrCreate(chatRequest.getConversationId());

                // Apply the profile values extracted by the composer, correcting
                // existing fields only when the extracted value differs. Rule-based
                // fallbacks backfill a value the LLM may have missed from the
                // student's own words, so the field never gets stuck re-asking.
                // The raw query is passed so a trailing "?" survives QueryGate's
                // cleaning and a question is not mistaken for an answer.
                Map<String, Object> appliedValues = withDeterministicFallbacks(
                        chatRequest.getConversationId(),
                        chatRequest.getQuery(),
                        cleanedQuery,
                        composerResult,
                        explicitlyNamedSchemes,
                        profile);
                profile = studentProfileService.applyExtracted(
                        chatRequest.getConversationId(),
                        appliedValues);

                boolean informational = intentResponse.isInformational();
                boolean namesScheme = !effectiveTargetSchemes.isEmpty();
                boolean selfAssessmentRequest = isSelfAssessmentRequest(cleanedQuery);

                boolean assessment = selfAssessmentRequest
                        || (!informational
                                && (hasPendingValidation || hasPendingDiscovery));

                // A profile statement with no question and no assessment intent
                // ("I'm in 12th") updates the profile. Answering it from the
                // corpus would be misleading, so acknowledge it deterministically.
                boolean profileOnlyTurn = !informational
                        && !assessment
                        && !hasPendingValidation
                        && !namesScheme
                        && isSelfStatement(cleanedQuery)
                        && !containsQuestionMark(chatRequest.getQuery())
                        && !appliedValues.isEmpty();

                if (profileOnlyTurn) {
                    chatResponse = profileAcknowledgement(appliedValues);
                    conversationMemoryManager.addAssistantMessage(
                            conversationId,
                            chatResponse
                    );
                } else {
                    // Informational queries use the read-only scope resolver:
                    // resolving through validateAll used to store the fields it
                    // would have asked, polluting the next turn's pending state.
                    ValidationResult validationResult = assessment
                            ? validationEngine.validateAll(
                                    profile,
                                    effectiveTargetSchemes,
                                    chatRequest.getConversationId())
                            : validationEngine.resolveScope(
                                    effectiveTargetSchemes,
                                    chatRequest.getConversationId());

                    log.info("Validation status {} mode {} target schemes {}",
                            validationResult.getStatus(),
                            validationResult.getMode(),
                            validationResult.getTargetSchemes());

                    if (assessment
                            && validationResult.getStatus() == ValidationStatus.MISSING_INFORMATION) {
                        String validationQuestion = validationResult.getQuestion();

                        chatResponse = new ChatResponse();
                        chatResponse.setResponse(validationQuestion);
                        chatResponse.setConversationMemorySummary(validationQuestion);
                        chatResponse.setSemanticMemorySummary(null);

                        conversationMemoryManager.addAssistantMessage(
                                conversationId,
                                chatResponse
                        );
                    } else {
                        // Once validation passes, the deterministic eligibility
                        // engine decides: match the collected profile against each
                        // scheme's target criteria. Discovery has no explicit
                        // scheme, so its target list is every scheme. If no scheme
                        // is in scope at all, fall back to grounded retrieval.
                        List<SchemeType> targetSchemes = validationResult.getTargetSchemes();
                        boolean useEligibility = assessment
                                && targetSchemes != null && !targetSchemes.isEmpty();

                        if (!assessment) {
                            log.info("Informational query routed to retrieval for schemes {}",
                                    targetSchemes);
                        }

                        ChatResponse response;
                        boolean temporaryUnavailable = false;
                        if (useEligibility) {
                            EligibilityReport eligibilityReport =
                                    eligibilityEngine.assessAll(profile, targetSchemes);
                            String answer = eligibilityAnswerFormatter.format(
                                    validationResult.getMode(), eligibilityReport);

                            log.info("Eligibility verdicts {} for schemes {}",
                                    eligibilityReport.assessments().stream()
                                            .map(a -> a.scheme() + "=" + a.verdict())
                                            .toList(),
                                    targetSchemes);

                            response = new ChatResponse();
                            response.setResponse(answer);
                            response.setConversationMemorySummary(answer);
                            response.setSemanticMemorySummary(null);
                        } else {
                            // Reformulate Query through combinedQuery, semanticMemories....
                            // and student profile to return the best possible
                            // scholarship chunks. The resolved scheme travels with the
                            // query so retrieval can filter on it.
                            ReformulatedQuery reformulatedQuery =
                                    retrievalQueryReformulator.
                                            reformulate(
                                                    retrievalQuery,
                                                    semanticMemories,
                                                    profile,
                                                    validationResult
                                            );

                            log.info("Reformulated query {} for schemes {}",
                                    reformulatedQuery.query(),
                                    reformulatedQuery.schemeNames());

                            // Search the knowledge base. The resolved scheme list is a
                            // hard filter so another scheme's rules can never be
                            // presented as this scheme's answer.
                            try {
                                List<ScholarshipChunkMatch> matches =
                                        scholarshipRetrieval.search(
                                                reformulatedQuery.query(),
                                                reformulatedQuery.candidates());

                                log.info("Retrieved {} chunks: {}", matches.size(),
                                        matches.stream().map(ScholarshipChunkMatch::citation).toList());

                                // Answer strictly from the retrieved chunks, using the
                                // composed query so a resolved follow-up ("what about
                                // its income limit?") is answered, not the fragment.
                                response = scholarshipAnswerGenerator.answer(
                                        retrievalQuery,
                                        matches);
                            } catch (RetrievalUnavailableException | GenerationUnavailableException e) {
                                // An outage must never be reported as "the guidelines
                                // do not contain this". Keep the failure explicit and
                                // do not write the transient message into memory.
                                log.error("Scholarship knowledge service unavailable", e);
                                temporaryUnavailable = true;
                                response = ChatResponse.noResult(TEMPORARY_UNAVAILABLE_ANSWER);
                            }

                        }

                        // Save semantic memory for this exchange. Stored text
                        // is embedded without the query instruction, and the
                        // assistant turn gets its own embedding instead of
                        // reusing the user query vector. A transient outage is
                        // not a real answer, so it is never stored.
                        if (!temporaryUnavailable) {
                            try {
                                String assistantContext = response.getSemanticMemorySummary() != null
                                        ? response.getSemanticMemorySummary()
                                        : response.getResponse();
                                semanticMemoryManager.addSemanticMemory(
                                        new SemanticMemoryRequest(
                                                conversationId,
                                                enrichedQuery,
                                                queryEmbedding.embed(enrichedQuery),
                                                assistantContext,
                                                queryEmbedding.embed(assistantContext)
                                        )
                                );
                            } catch (Exception e) {
                                log.warn("Failed to save semantic memory for conversation {}", conversationId, e);
                            }
                        }

                        conversationMemoryManager.addAssistantMessage(
                                conversationId,
                                response
                        );

                        chatResponse = response;
                    }
                }
            }

            case SCHEME_CATALOG -> {
                // Fallback for paraphrases the deterministic pre-check did not
                // recognise: the model classified the turn as a catalogue
                // question, so answer it from the registry rather than
                // retrieving a fact that no guideline contains.
                chatResponse = catalogResponse(chatRequest, conversationId);
            }

            case GENERAL_CHAT -> {

                // Add current user message to conversation memory
                conversationMemoryManager.addUserMessage(chatRequest);

                // General chat can still carry a profile fact ("I'm in 12th").
                // The composer is not run for GENERAL_CHAT, so a deterministic
                // self-statement detector keeps that fact from being lost.
                Map<String, Object> statedValues = selfStatedProfileValues(cleanedQuery);
                if (!statedValues.isEmpty()) {
                    studentProfileService.getOrCreate(conversationId);
                    studentProfileService.applyExtracted(conversationId, statedValues);
                }

                boolean profileOnly = !statedValues.isEmpty()
                        && !containsQuestionMark(chatRequest.getQuery())
                        && !isSelfAssessmentRequest(cleanedQuery);

                if (profileOnly) {
                    chatResponse = profileAcknowledgement(statedValues);
                } else {
                    try {
                        String response = streamAnswer(
                                enrichedQuery,
                                recentConversationMessages
                        );

                        chatResponse = new ChatResponse();
                        chatResponse.setResponse(response);
                        chatResponse.setConversationMemorySummary(null);
                        chatResponse.setSemanticMemorySummary(null);
                    } catch (RuntimeException e) {
                        // The fallback model chain can still be unavailable.
                        // Return an honest, actionable message instead of a 500.
                        log.error("General chat model call failed", e);
                        chatResponse = new ChatResponse();
                        chatResponse.setResponse(
                                "I could not reach the language model just now. "
                                        + "Please try again in a moment, or ask about a "
                                        + "specific scholarship scheme.");
                        chatResponse.setConversationMemorySummary(null);
                        chatResponse.setSemanticMemorySummary(null);
                    }
                }

                conversationMemoryManager.addAssistantMessage(
                        conversationId,
                        chatResponse
                );
            }

            case UNKNOWN -> {

                conversationMemoryManager.addUserMessage(chatRequest);

                chatResponse = new ChatResponse();

                chatResponse.setResponse(
                        "I'm sorry, I couldn't understand your question. "
                                + "Please ask about scholarships, eligibility, deadlines, "
                                + "applications, or financial aid."
                );
                chatResponse.setConversationMemorySummary(null);
                chatResponse.setSemanticMemorySummary(null);

                conversationMemoryManager.addAssistantMessage(
                        conversationId,
                        chatResponse
                );
            }

            default -> {

                chatResponse = new ChatResponse();

                chatResponse.setResponse(
                        "I'm sorry, I couldn't process your request. Please try again."
                );
                chatResponse.setConversationMemorySummary(null);
                chatResponse.setSemanticMemorySummary(null);
            }
        }

        return chatResponse;
    }


    /**
     * Reconciles the composer's scheme targets with the current turn.
     *
     * <p>A scheme explicitly named in the current query always wins over a
     * scheme that only appears in conversation history; otherwise the same
     * factual question could silently change scope after an unrelated turn.
     * The composer is trusted for history-derived schemes only when the query
     * actually depends on that history (pronouns, short follow-ups, "what
     * about...").
     */
    private List<String> effectiveTargetSchemes(
            List<SchemeType> explicitlyNamed,
            List<String> composerTargets,
            boolean contextTrusted) {

        LinkedHashSet<String> schemes = new LinkedHashSet<>();
        if (explicitlyNamed != null && !explicitlyNamed.isEmpty()) {
            explicitlyNamed.forEach(scheme -> schemes.add(scheme.name()));
            // A scheme named in the current query is authoritative. Composer
            // targets derived from history must not widen that scope, or
            // "what about Top Class SC?" after an Ishan Uday turn would assess
            // both schemes and answer a question the student did not ask.
            return List.copyOf(schemes);
        }

        // With no explicit scheme, composer targets are trusted only when the
        // query depends on context. Resolve them through the registry and drop
        // unknown text rather than letting it fall back to every scheme.
        if (contextTrusted && composerTargets != null) {
            for (String raw : composerTargets) {
                List<SchemeType> resolved = validationEngine.schemesNamedIn(raw);
                if (resolved != null) {
                    resolved.forEach(scheme -> schemes.add(scheme.name()));
                }
            }
        }
        return List.copyOf(schemes);
    }

    private ChatResponse catalogResponse(ChatRequest chatRequest, String conversationId) {
        return deterministicResponse(
                chatRequest, conversationId, schemeCatalogResponder.answer());
    }

    private ChatResponse deterministicResponse(
            ChatRequest chatRequest, String conversationId, String answer) {
        conversationMemoryManager.addUserMessage(chatRequest);

        ChatResponse response = new ChatResponse();
        response.setResponse(answer);
        response.setConversationMemorySummary(answer);
        response.setSemanticMemorySummary(null);

        conversationMemoryManager.addAssistantMessage(conversationId, response);
        return response;
    }

    private String streamAnswer(String query, List<ChatMessage> recentConversationMessages) {
        StringBuilder answer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        String prompt = buildPrompt(query, recentConversationMessages);

        openAiStreamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                answer.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("LLM streaming timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }

        return answer.toString();
    }

    /**
     * Hybrid safety net over the LLM's extracted values. Two deterministic
     * layers run: first a per-field detector for values the model missed
     * entirely, then a pending-answer resolver that maps a plain reply onto the
     * field the assistant just asked about. Only absent fields are filled; the
     * model's output is never overridden.
     *
     * <p>The detectors are additionally restricted to fields the stored profile
     * does not hold yet. They match bare tokens such as "sc" or "msc" anywhere
     * in the message, so without that restriction a student asking
     * "what scholarships are there for SC students?" would have their own
     * GENERAL category overwritten with SC, and the eligibility verdict would
     * flip. A reply that is genuinely an answer is handled by the resolver
     * below, which only runs for fields the assistant just asked about.
     */
    private static final java.util.regex.Pattern SELF_STATEMENT =
            java.util.regex.Pattern.compile(
                    "\\b(i\\s+am|i['\u2019]m|i\\s+belong|i\\s+study|i\\s+have|"
                            + "i\\s+completed|i\\s+am\\s+in|my)\\b",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * A request to be judged, matched or recommended, as opposed to a factual
     * question about a scheme. Bare "eligibility" or "qualification" wording is
     * deliberately not enough: "What are the eligibility criteria?" is a
     * factual retrieval question. Only a first-person assessment request, a
     * discovery request or an ongoing pending flow starts validation.
     */
    private static final java.util.regex.Pattern SELF_ASSESSMENT_REQUEST =
            java.util.regex.Pattern.compile(
                    "\\b(am|are|is)\\s+i\\s+eligib\\w*"
                            + "|\\bdo\\s+i\\s+(qualify|meet)\\b"
                            + "|\\b(can|will|would|may)\\s+i\\s+(get|receive)\\b"
                            + "[^.?!]{0,60}\\b(scholarship|scheme|benefit|money|amount|eligible|eligibility)\\b"
                            + "|\\b(can|will|would|may)\\s+i\\s+(apply|qualify)\\b"
                            + "|\\bcheck\\s+(my|our)\\s+eligib\\w*"
                            + "|\\bmy\\s+eligib\\w*"
                            + "|\\bwhich\\s+scholarships?\\s+(am|are)\\s+i\\s+eligib\\w*"
                            + "|\\bwhich\\s+scholarships?\\s+(can|may|will|would)\\s+i\\s+"
                            + "(get|apply|receive|qualify)\\b"
                            + "|\\bwhich\\s+scholarships?\\s+(suit|fit)\\s+me\\b"
                            + "|\\b(recommend|suggest)\\w*\\s+(me|a|any)\\b"
                            + "|\\bi\\s+(need|want)\\s+(a\\s+)?scholarship\\b",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * A question about where to obtain or submit the form is an informational
     * access question, even though it contains "can I get ... scholarship".
     * Without this guard it was misread as a request to assess the student and
     * started profile collection.
     */
    private static final java.util.regex.Pattern FACTUAL_APPLICATION_ACCESS_REQUEST =
            java.util.regex.Pattern.compile(
                    "\\b(where|how|when)\\b[^.?!]{0,40}"
                            + "\\b(can|do|should|could)\\s+i\\s+"
                            + "(get|find|download|fill|submit|apply)\\b"
                            + "[^.?!]{0,60}\\b(form|application|portal|website|online)\\b"
                            // "Can I get the application form online?" is an
                            // access question even without where/how/when.
                            + "|\\b(can|do|should|could)\\s+i\\s+"
                            + "(get|find|download|fill|submit|access)\\b"
                            + "[^.?!]{0,60}\\b(application|form|portal|website|online|status)\\b",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> PROFILE_FIELD_LABELS = Map.ofEntries(
            Map.entry("EDUCATION_LEVEL", "education level"),
            Map.entry("COURSE", "course"),
            Map.entry("COURSE_TYPE", "course type"),
            Map.entry("CURRENT_YEAR", "current year"),
            Map.entry("CURRENT_CLASS", "current class"),
            Map.entry("REGULAR_MODE", "regular mode"),
            Map.entry("STUDY_LEVEL", "study level"),
            Map.entry("HAS_PRIOR_DEGREE", "prior degree status"),
            Map.entry("CLASS12_PERCENTILE", "Class 12 percentile"),
            Map.entry("CLASS12_PASSED_YEAR", "Class 12 passing year"),
            Map.entry("BOARD", "board"),
            Map.entry("STREAM", "stream"),
            Map.entry("PREVIOUS_CLASS_MARKS", "previous class marks"),
            Map.entry("APPLICATION_TYPE", "application type"),
            Map.entry("ADMISSION_RANK", "admission rank"),
            Map.entry("NATIONALITY", "nationality"),
            Map.entry("GENDER", "gender"),
            Map.entry("DATE_OF_BIRTH", "date of birth"),
            Map.entry("ANNUAL_FAMILY_INCOME", "annual family income"),
            Map.entry("SOCIAL_CATEGORY", "social category"),
            Map.entry("DOMICILE_STATE", "domicile state"),
            Map.entry("INSTITUTION_NAME", "institution name"),
            Map.entry("INSTITUTION_TYPE", "institution type"),
            Map.entry("RECEIVING_OTHER_SCHOLARSHIP", "other scholarship status"),
            Map.entry("SIBLINGS_RECEIVING_BENEFIT", "sibling benefit status"),
            Map.entry("HAS_DISABILITY", "disability status"),
            Map.entry("DISABILITY_PERCENTAGE", "disability percentage"),
            Map.entry("DISABILITY_TYPE", "disability type"),
            Map.entry("HAS_VALID_DISABILITY_CERTIFICATE", "disability certificate status"),
            Map.entry("HAS_UDID_OR_UDID_ENROLLMENT", "UDID status"));

    private Map<String, Object> withDeterministicFallbacks(
            String conversationId,
            String rawQuery,
            String cleanedQuery,
            QueryComposerResult composerResult,
            List<SchemeType> explicitlyNamedSchemes,
            StudentProfileDto profile) {

        Map<String, Object> merged = new LinkedHashMap<>();
        if (composerResult.getExtractedValues() != null) {
            merged.putAll(composerResult.getExtractedValues());
        }

        List<StudentProfileField> asked =
                pendingValidationState.askedFields(conversationId);
        boolean selfStated = isSelfStatement(rawQuery);
        boolean answeringAskedEducation = asked.contains(StudentProfileField.EDUCATION_LEVEL);
        boolean answeringAskedCategory = asked.contains(StudentProfileField.SOCIAL_CATEGORY);
        boolean answeringAskedCourse = asked.contains(StudentProfileField.COURSE);
        boolean answeringAskedYear = asked.contains(StudentProfileField.CURRENT_YEAR);
        boolean answeringAskedIncome = asked.contains(StudentProfileField.ANNUAL_FAMILY_INCOME);
        boolean answeringAskedDomicile = asked.contains(StudentProfileField.DOMICILE_STATE);
        boolean answeringAskedRegular = asked.contains(StudentProfileField.REGULAR_MODE);
        boolean answeringAskedPriorDegree = asked.contains(StudentProfileField.HAS_PRIOR_DEGREE);
        boolean answeringAskedApplicationType = asked.contains(StudentProfileField.APPLICATION_TYPE);

        // A model extraction is only a proposal. Keep it only when the user
        // actually spoke about themselves, or when they are answering a field
        // the assistant just asked for. A factual question that merely
        // mentions a target group must not populate the student's profile.
        if (!selfStated) {
            if (asked.isEmpty()) {
                merged.clear();
            } else {
                List<String> askedNames = asked.stream().map(Enum::name).toList();
                merged.keySet().retainAll(askedNames);
            }
            if (!answeringAskedCategory && merged.remove("SOCIAL_CATEGORY") != null) {
                log.debug("Ignoring SOCIAL_CATEGORY extracted from a turn that never "
                        + "states it about themselves");
            }
            if (!answeringAskedCourse && merged.remove("COURSE") != null) {
                log.debug("Ignoring COURSE extracted from a turn that never states it "
                        + "about themselves");
            }
        }

        if (isAbsent(merged.get("EDUCATION_LEVEL"))
                && (profile == null || isAbsent(profile.getEducationLevel()))
                && (selfStated || answeringAskedEducation)) {
            profileValueNormalizer.detectEducationLevel(rawQuery).ifPresent(level -> {
                merged.put("EDUCATION_LEVEL", level);
                Integer classNumber = classNumber(level);
                if (classNumber != null
                        && isAbsent(merged.get("CURRENT_CLASS"))
                        && (profile == null || isAbsent(profile.getCurrentClass()))) {
                    merged.put("CURRENT_CLASS", classNumber);
                }
            });
        }
        if (isAbsent(merged.get("SOCIAL_CATEGORY"))
                && (profile == null || isAbsent(profile.getSocialCategory()))
                && (selfStated || answeringAskedCategory)) {
            profileValueNormalizer.detectSocialCategory(rawQuery)
                    .ifPresent(category -> merged.put("SOCIAL_CATEGORY", category));
        }
        if (isAbsent(merged.get("COURSE"))
                && (profile == null || isAbsent(profile.getCourse()))
                && (selfStated || answeringAskedCourse)) {
            Optional<String> course = selfStated
                    ? profileValueNormalizer.detectCourseForProfile(rawQuery)
                    : Optional.empty();
            if (course.isEmpty() && answeringAskedCourse) {
                course = profileValueNormalizer.detectCourse(rawQuery);
            }
            course.ifPresent(value -> merged.put("COURSE", value));
        }

        if (selfStated || answeringAskedYear) {
            if (isAbsent(merged.get("CURRENT_YEAR"))
                    && (profile == null || profile.getCurrentYear() == null)) {
                profileValueNormalizer.detectCurrentYear(rawQuery)
                        .ifPresent(year -> merged.put("CURRENT_YEAR", year));
            }
        }
        if (selfStated || answeringAskedIncome) {
            if (isAbsent(merged.get("ANNUAL_FAMILY_INCOME"))
                    && (profile == null || profile.getAnnualFamilyIncome() == null)) {
                profileValueNormalizer.detectAnnualFamilyIncome(rawQuery)
                        .ifPresent(income -> merged.put("ANNUAL_FAMILY_INCOME", income));
            }
        }
        if (selfStated || answeringAskedDomicile) {
            if (isAbsent(merged.get("DOMICILE_STATE"))
                    && (profile == null || isAbsent(profile.getDomicileState()))) {
                profileValueNormalizer.detectDomicileState(rawQuery)
                        .ifPresent(state -> merged.put("DOMICILE_STATE", state));
            }
        }
        if (selfStated || answeringAskedRegular) {
            if (isAbsent(merged.get("REGULAR_MODE"))
                    && (profile == null || profile.getRegularMode() == null)) {
                profileValueNormalizer.detectRegularMode(rawQuery)
                        .ifPresent(mode -> merged.put("REGULAR_MODE", mode));
            }
        }
        if (selfStated || answeringAskedPriorDegree) {
            if (isAbsent(merged.get("HAS_PRIOR_DEGREE"))
                    && (profile == null || profile.getCompletedUGDegree() == null)) {
                profileValueNormalizer.detectPriorDegree(rawQuery)
                        .ifPresent(completed -> merged.put("HAS_PRIOR_DEGREE", completed));
            }
        }
        if (selfStated || answeringAskedApplicationType) {
            if (isAbsent(merged.get("APPLICATION_TYPE"))
                    && (profile == null || isAbsent(profile.getApplicationType()))) {
                profileValueNormalizer.detectApplicationType(rawQuery)
                        .ifPresent(type -> merged.put("APPLICATION_TYPE", type));
            }
        }
        if (selfStated && isAbsent(merged.get("NATIONALITY"))
                && (profile == null || isAbsent(profile.getNationality()))) {
            profileValueNormalizer.detectNationality(rawQuery)
                    .ifPresent(nationality -> merged.put("NATIONALITY", nationality));
        }

        // When no new scheme was named this turn, the student is likely
        // answering the question just asked. Resolve the reply onto the asked
        // field so a valid answer is never dropped and re-asked. The raw query
        // is used here because QueryGate strips a trailing "?"; without it a
        // question could be resolved as an answer ("... don't have a
        // disability?" -> false).
        // A scheme explicitly named in this turn starts a new scope, so the
        // reply is not an answer to the field asked for the previous scheme.
        // Inherited targets do not have that effect: the student must still be
        // able to answer the question they were just asked.
        boolean continuingExchange = explicitlyNamedSchemes == null
                || explicitlyNamedSchemes.isEmpty();
        if (continuingExchange && !asked.isEmpty()) {
            pendingAnswerResolver.resolve(asked, rawQuery, merged.keySet())
                    .forEach(merged::putIfAbsent);
        }
        return merged;
    }

    /**
     * Deterministic profile facts from a first-person statement, used by
     * GENERAL_CHAT where the query composer is not run. Restricted to
     * self-statements so "explain SC" or "what about MSc?" cannot become the
     * student's own category or course.
     */
    private Map<String, Object> selfStatedProfileValues(String query) {
        if (!isSelfStatement(query)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        profileValueNormalizer.detectEducationLevel(query).ifPresent(level -> {
            values.put("EDUCATION_LEVEL", level);
            Integer classNumber = classNumber(level);
            if (classNumber != null) {
                values.put("CURRENT_CLASS", classNumber);
            }
        });
        profileValueNormalizer.detectSocialCategory(query)
                .ifPresent(category -> values.put("SOCIAL_CATEGORY", category));
        profileValueNormalizer.detectCourseForProfile(query)
                .ifPresent(course -> values.put("COURSE", course));
        profileValueNormalizer.detectCurrentYear(query)
                .ifPresent(year -> values.put("CURRENT_YEAR", year));
        profileValueNormalizer.detectAnnualFamilyIncome(query)
                .ifPresent(income -> values.put("ANNUAL_FAMILY_INCOME", income));
        profileValueNormalizer.detectDomicileState(query)
                .ifPresent(state -> values.put("DOMICILE_STATE", state));
        profileValueNormalizer.detectRegularMode(query)
                .ifPresent(mode -> values.put("REGULAR_MODE", mode));
        profileValueNormalizer.detectPriorDegree(query)
                .ifPresent(completed -> values.put("HAS_PRIOR_DEGREE", completed));
        profileValueNormalizer.detectApplicationType(query)
                .ifPresent(type -> values.put("APPLICATION_TYPE", type));
        profileValueNormalizer.detectNationality(query)
                .ifPresent(nationality -> values.put("NATIONALITY", nationality));
        return values;
    }

    private ChatResponse profileAcknowledgement(Map<String, Object> savedValues) {
        List<String> labels = savedValues.keySet().stream()
                .map(field -> PROFILE_FIELD_LABELS.getOrDefault(field, field))
                .distinct()
                .toList();
        String saved = labels.isEmpty()
                ? "that"
                : "your " + String.join(", ", labels.subList(0, labels.size() - 1))
                        + (labels.size() > 1 ? " and " : "")
                        + labels.getLast();

        String response = "Thanks \u2014 I've saved " + saved
                + " to your profile. You can now ask me which scholarships you may be "
                + "eligible for, or ask about a specific scheme's rules.";

        ChatResponse acknowledgement = new ChatResponse();
        acknowledgement.setResponse(response);
        acknowledgement.setConversationMemorySummary(response);
        acknowledgement.setSemanticMemorySummary(null);
        return acknowledgement;
    }

    private static boolean isSelfStatement(String query) {
        return query != null && SELF_STATEMENT.matcher(query).find();
    }

    private static boolean containsQuestionMark(String query) {
        return query != null && query.indexOf('?') >= 0;
    }

    private static boolean isSelfAssessmentRequest(String query) {
        if (query == null || FACTUAL_APPLICATION_ACCESS_REQUEST.matcher(query).find()) {
            return false;
        }
        return SELF_ASSESSMENT_REQUEST.matcher(query).find();
    }

    private static boolean isAbsent(Object value) {
        return value == null || (value instanceof CharSequence text && text.toString().isBlank());
    }

    private static Integer classNumber(String level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case "CLASS_9" -> 9;
            case "CLASS_10" -> 10;
            case "CLASS_11" -> 11;
            case "CLASS_12" -> 12;
            default -> null;
        };
    }

    private String buildPrompt(String query, List<ChatMessage> recentConversationMessages) {
        String history = recentConversationMessages.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .collect(Collectors.joining("\n"));

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful scholarship assistant. Answer using the conversation history.\n")
                .append("For general conversation and definitions, answer briefly. Do not invent ")
                .append("scholarship-specific facts such as income limits, amounts, deadlines or ")
                .append("documents. If asked for those, say the scheme documents are the source of ")
                .append("truth and invite the user to ask about a specific scheme.\n\n");

        if (!history.isBlank()) {
            prompt.append("Conversation history:\n").append(history).append("\n\n");
        }

        prompt.append("User query: ").append(query);
        return prompt.toString();
    }
}