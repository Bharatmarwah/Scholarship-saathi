package com.bharat.scholarship_rag_backend.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.dto.response.ChatResponse;
import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScholarshipAnswerGeneratorTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private ScholarshipChunkMatch match(ContentType contentType, String content) {
        return new ScholarshipChunkMatch(
                ID, SchemeType.TOP_CLASS_PWD, content, contentType,
                "top-class-pwd/guideline.md", "4. ELIGIBILITY", null,
                3, 3, 1, 400, 0.2, null, 0.1,
                "TOP CLASS PWD, 4. ELIGIBILITY, page 3");
    }

    @Test
    void refusesToAnswerWhenNothingWasRetrieved() {
        ScholarshipAnswerGenerator generator = generator();

        assertEquals(ScholarshipAnswerGenerator.NO_CONTEXT_ANSWER, generator.answer("q", List.of()).getResponse());
        assertEquals(ScholarshipAnswerGenerator.NO_CONTEXT_ANSWER, generator.answer("q", null).getResponse());
    }

    @Test
    void rendersPassagesWithProvenanceAndAuthorityLabel() {
        ScholarshipAnswerGenerator generator = generator();

        String rendered = generator.renderPassages(List.of(
                match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh."),
                match(ContentType.FAQ, "Only two siblings may benefit.")));

        assertTrue(rendered.startsWith("[Passage 1] NORMATIVE | TOP CLASS PWD, 4. ELIGIBILITY, page 3"));
        assertTrue(rendered.contains("Income limit is Rs. 8 lakh."));
        assertTrue(rendered.contains("[Passage 2] RESTATEMENT"));
        assertTrue(rendered.contains("Only two siblings may benefit."));
    }

    @Test
    void guidelineIsLabelledNormativeAndFaqIsLabelledRestatement() {
        ScholarshipAnswerGenerator generator = generator();

        assertTrue(generator.renderPassages(
                List.of(match(ContentType.GUIDELINE, "x"))).contains("NORMATIVE"));
        assertTrue(generator.renderPassages(
                List.of(match(ContentType.FAQ, "x"))).contains("RESTATEMENT"));
        assertTrue(generator.renderPassages(
                List.of(match(ContentType.ANNEXURE, "x"))).contains("NORMATIVE"));
    }

    @Test
    void everyPassageIsNumberedForCitation() {
        ScholarshipAnswerGenerator generator = generator();

        String rendered = generator.renderPassages(List.of(
                match(ContentType.GUIDELINE, "one"),
                match(ContentType.GUIDELINE, "two"),
                match(ContentType.GUIDELINE, "three")));

        for (int i = 1; i <= 3; i++) {
            assertTrue(rendered.contains("[Passage " + i + "]"), "missing passage " + i);
        }
        assertFalse(rendered.contains("[Passage 4]"));
    }

    @Test
    void parsesJsonWrappedInAMarkdownFence() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                ```json
                {"response": "The income limit is Rs. 8 lakh [1].",
                 "conversationMemorySummary": "Income limit discussed.",
                 "semanticMemorySummary": "Top Class SC income limit."}
                ```
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh.")));

        assertEquals("The income limit is Rs. 8 lakh [1].", answer.getResponse());
        assertEquals("Income limit discussed.", answer.getConversationMemorySummary());
    }

    @Test
    void acceptsRawTextWhenItCarriesAValidCitation() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("The income limit is eight lakh [1].");

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is eight lakh.")));

        assertEquals("The income limit is eight lakh [1].", answer.getResponse());
        assertFalse(answer.getResponse().contains("could not find"),
                "a parse failure must not be reported as missing information");
    }

    @Test
    void rejectsAnUncitedAnswerInsteadOfPassingItThrough() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh.")));

        assertEquals(ScholarshipAnswerGenerator.UNVERIFIED_ANSWER, answer.getResponse());
    }

    @Test
    void rejectsACitationToAPassageThatWasNotRetrieved() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh [2]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh.")));

        assertEquals(ScholarshipAnswerGenerator.UNVERIFIED_ANSWER, answer.getResponse());
    }

    @Test
    void rejectsANumberThatDoesNotAppearInThePassages() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 10 lakh [1]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh.")));

        assertEquals(ScholarshipAnswerGenerator.UNVERIFIED_ANSWER, answer.getResponse());
    }

    @Test
    void rejectsANumberThatOnlyAppearsAsPartOfALongerNumber() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 1 lakh [1]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 10 lakh.")));

        assertEquals(ScholarshipAnswerGenerator.UNVERIFIED_ANSWER, answer.getResponse());
    }

    @Test
    void rejectsANumberTakenFromAPassageTheAnswerDidNotCite() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh [1] and the disability requirement is 40% [1]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(
                        match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh."),
                        match(ContentType.GUIDELINE, "Disability requirement is 40%.")));

        assertEquals(ScholarshipAnswerGenerator.UNVERIFIED_ANSWER, answer.getResponse());
    }

    @Test
    void acceptsNumbersFromEachPassageTheAnswerCites() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh [1] and the disability requirement is 40% [2]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(
                        match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh."),
                        match(ContentType.GUIDELINE, "Disability requirement is 40%.")));

        assertEquals("The income limit is Rs. 8 lakh [1] and the disability requirement is 40% [2].",
                answer.getResponse());
    }

    @Test
    void acceptsPassageLabelCitationsTheModelSeesInTheContext() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh [Passage 1]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh.")));

        assertEquals("The income limit is Rs. 8 lakh [Passage 1].", answer.getResponse());
    }

    @Test
    void acceptsMultiplePassageLabelCitationsInOneBracket() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh and the disability requirement is 40% [Passage 1, Passage 2]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(
                        match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh."),
                        match(ContentType.GUIDELINE, "Disability requirement is 40%.")));

        assertEquals("The income limit is Rs. 8 lakh and the disability requirement is 40% "
                        + "[Passage 1, Passage 2].",
                answer.getResponse());
    }

    @Test
    void rejectsAPassageLabelCitationThatWasNotRetrieved() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh [Passage 2]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh.")));

        assertEquals(ScholarshipAnswerGenerator.UNVERIFIED_ANSWER, answer.getResponse());
    }

    @Test
    void stillChecksNumbersInsideNonCitationBrackets() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("""
                {"response": "The income limit is Rs. 8 lakh [1]. Disability is 40% [note 40]."}
                """);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("q", List.of(
                        match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh."),
                        match(ContentType.GUIDELINE, "Disability requirement is 40%.")));

        assertEquals(ScholarshipAnswerGenerator.UNVERIFIED_ANSWER, answer.getResponse());
    }

    @Test
    void modelFailureIsReportedAsAnOutageNotAsMissingEvidence() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("provider down"));

        ScholarshipAnswerGenerator generator =
                new ScholarshipAnswerGenerator(model, new ObjectMapper());

        assertThrows(GenerationUnavailableException.class,
                () -> generator.answer("income limit", List.of(
                        match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh."))));
    }

    @Test
    void refusesWhenNoPassageSharesAMeaningfulTermWithTheQuery() {
        ChatModel model = mock(ChatModel.class);

        ChatResponse answer = new ScholarshipAnswerGenerator(model, new ObjectMapper())
                .answer("what is the weather today",
                        List.of(match(ContentType.GUIDELINE, "Income limit is Rs. 8 lakh.")));

        assertEquals(ScholarshipAnswerGenerator.NO_CONTEXT_ANSWER, answer.getResponse());
    }

    /**
     * A model built against an unreachable host. It is never called, because
     * the tests that use it only exercise the no-context and rendering paths.
     */
    private static ScholarshipAnswerGenerator generator() {
        return new ScholarshipAnswerGenerator(dev.langchain4j.model.openai.OpenAiChatModel.builder()
                .baseUrl("http://localhost:1")
                .apiKey("unused")
                .modelName("unused")
                .maxRetries(0)
                .build(), new tools.jackson.databind.ObjectMapper());
    }
}