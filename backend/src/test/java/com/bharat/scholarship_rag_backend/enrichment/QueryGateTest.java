package com.bharat.scholarship_rag_backend.enrichment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryGateTest {

    private final QueryGate queryGate = new QueryGate();

    @Test
    void administrativeExpensesQuestionIsSelfContained() {
        assertFalse(queryGate.needsContext(
                "what are the administractive expenses for top class sc ?"));
    }

    @Test
    void schemeGuidelinesQuestionIsSelfContained() {
        assertFalse(queryGate.needsContext("Where are Scheme Guidelines available?"));
    }

    @Test
    void incomeLimitQuestionIsSelfContained() {
        assertFalse(queryGate.needsContext(
                "What is the family income limit for Top Class SC?"));
    }

    @Test
    void genuineFollowUpsStillNeedContext() {
        assertTrue(queryGate.needsContext("what about its income limit?"));
        assertTrue(queryGate.needsContext("sorry?"));
        assertTrue(queryGate.needsContext("why?"));
        assertTrue(queryGate.needsContext("documents?"));
    }

    @Test
    void decimalAmountsKeepTheirDecimalPoint() {
        assertEquals("4.5 lakh", queryGate.clean("4.5 lakh?"));
        assertEquals("annual income is 2.5 lakh", queryGate.clean("annual income is 2.5 lakh."));
    }

    @Test
    void plusTwoMarkerKeepsItsPlusSign() {
        assertEquals("+2", queryGate.clean("+2?"));
    }
}
