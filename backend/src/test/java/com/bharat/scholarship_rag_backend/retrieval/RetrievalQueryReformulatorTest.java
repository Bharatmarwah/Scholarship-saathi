package com.bharat.scholarship_rag_backend.retrieval;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.validator.ValidationMode;
import com.bharat.scholarship_rag_backend.validator.ValidationResult;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalQueryReformulatorTest {

    private final ChatModel chatModel = mock(ChatModel.class);

    private final RetrievalQueryReformulator reformulator =
            new RetrievalQueryReformulator(chatModel);

    private ReformulatedQuery reformulate(
            String combinedQuery,
            SchemeType targetScheme,
            List<SchemeType> targetSchemes) {

        ValidationResult validation = new ValidationResult();
        validation.setMode(targetScheme == null
                ? ValidationMode.SCHOLARSHIP_DISCOVERY
                : ValidationMode.SPECIFIC_SCHEME);
        validation.setTargetScheme(targetScheme);
        validation.setTargetSchemes(targetSchemes);

        return reformulator.reformulate(
                combinedQuery,
                List.of(),
                null,
                validation);
    }

    private void modelReturns(String response) {
        when(chatModel.chat(anyString())).thenReturn(response);
    }

    @Test
    void namedSchemeIsPrefixedAndScoped() {
        modelReturns("annual family income limit for the scheme");

        ReformulatedQuery result = reformulate(
                "what is the income limit",
                SchemeType.TOP_CLASS_SC,
                List.of(SchemeType.TOP_CLASS_SC));

        assertEquals("TOP CLASS SC: annual family income limit for the scheme",
                result.query());
        assertEquals(List.of("TOP_CLASS_SC"), result.schemeNames());
        assertEquals(SchemeType.TOP_CLASS_SC, result.scheme());
        assertEquals(List.of(SchemeType.TOP_CLASS_SC), result.candidates());
        assertTrue(result.isScoped());
    }

    @Test
    void singleCandidateIsNotTreatedAsNamedScheme() {
        modelReturns("query text");

        ReformulatedQuery result = reformulate(
                "q",
                null,
                List.of(SchemeType.TOP_CLASS_SC));

        assertNull(result.scheme());
        assertEquals(List.of(SchemeType.TOP_CLASS_SC), result.candidates());
        assertEquals(List.of("TOP_CLASS_SC"), result.schemeNames());
    }

    @Test
    void discoveryModeKeepsEveryCandidateAndAddsNoPrefix() {
        modelReturns("income limit across schemes");

        ReformulatedQuery result = reformulate(
                "what is the income limit",
                null,
                List.of(SchemeType.PM_USP_CSSS, SchemeType.TOP_CLASS_SC));

        assertEquals("income limit across schemes", result.query());
        assertNull(result.scheme());
        assertEquals(
                List.of("PM_USP_CSSS", "TOP_CLASS_SC"),
                result.schemeNames());
        assertEquals(2, result.candidates().size());
        assertTrue(result.isScoped());
    }

    @Test
    void namedSchemeWinsOverCandidateList() {
        modelReturns("eligibility conditions");

        ReformulatedQuery result = reformulate(
                "am i eligible",
                SchemeType.TOP_CLASS_PWD,
                List.of(SchemeType.PM_USP_CSSS, SchemeType.TOP_CLASS_PWD));

        assertEquals(List.of(SchemeType.TOP_CLASS_PWD), result.candidates());
    }

    @Test
    void blankModelOutputFallsBackToCombinedQuery() {
        modelReturns("   ");

        ReformulatedQuery result = reformulate(
                "what is the income limit",
                SchemeType.ISHAN_UDAY,
                List.of(SchemeType.ISHAN_UDAY));

        assertEquals("ISHAN UDAY: what is the income limit", result.query());
        assertEquals(List.of("ISHAN_UDAY"), result.schemeNames());
    }

    @Test
    void runawayModelOutputFallsBackToCombinedQuery() {
        String runaway = "x".repeat(500);
        modelReturns(runaway);

        ReformulatedQuery result = reformulate(
                "short query",
                SchemeType.PM_USP_CSSS,
                List.of(SchemeType.PM_USP_CSSS));

        assertEquals("PM USP CSSS: short query", result.query());
    }

    @Test
    void noSchemeAndNoCandidatesIsUnscoped() {
        modelReturns("some refined query");

        ReformulatedQuery result = reformulate("some query", null, null);

        assertEquals("some refined query", result.query());
        assertEquals(List.of(), result.candidates());
        assertFalse(result.isScoped());
    }

    @Test
    void nullValidationFallsBackToUnscoped() {
        modelReturns("query text");

        ReformulatedQuery result =
                reformulator.reformulate("q", List.of(), null, null);

        assertEquals("query text", result.query());
        assertNull(result.scheme());
        assertEquals(List.of(), result.candidates());
        assertFalse(result.isScoped());
    }

    @Test
    void unscopedFactoryProducesEmptySchemeScope() {
        ReformulatedQuery result = ReformulatedQuery.unscoped("query text");

        assertEquals("query text", result.query());
        assertEquals(List.of(), result.schemeNames());
        assertNull(result.scheme());
        assertEquals(List.of(), result.candidates());
        assertFalse(result.isScoped());
    }
}