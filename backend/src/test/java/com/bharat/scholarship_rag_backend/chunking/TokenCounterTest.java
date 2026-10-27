package com.bharat.scholarship_rag_backend.chunking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCounterTest {

    private final TokenCounter counter = new TokenCounter();

    @Test
    void countsRealTokensRatherThanCharacters() {
        String text = "## 2. ELIGIBILITY: (IX) The income of the parents of the student "
                + "should be below Rs. 04.50 lakh per annum.";

        int tokens = counter.count(text);

        System.out.println("tokens=" + tokens + " chars=" + text.length());
        assertTrue(tokens > 0);
        assertTrue(tokens < text.length(), "tokens must not equal raw character count");
    }

    @Test
    void denseNumericTableCostsMoreThanCharacterHeuristicSuggests() {
        String digits = "1. 2021-22 1500 2. 2022-23 1600 3. 2023-24 1700 4. 2024-25 1800 5. 2025-26 1900";

        System.out.println("digits tokens=" + counter.count(digits) + " chars=" + digits.length());
        assertTrue(counter.count(digits) > 0);
    }

    @Test
    void emptyAndNullAreZero() {
        assertEquals(0, counter.count(""));
        assertEquals(0, counter.count(null));
    }
}