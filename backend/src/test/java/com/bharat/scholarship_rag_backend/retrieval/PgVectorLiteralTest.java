package com.bharat.scholarship_rag_backend.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class PgVectorLiteralTest {

    @Test
    void rendersVectorInPgvectorSyntax() {
        assertEquals("[0.1,0.2,0.3]", PgVectorLiteral.vector(new float[] {0.1f, 0.2f, 0.3f}));
    }

    @Test
    void rendersSingleElementAndWholeNumbers() {
        assertEquals("[1.0]", PgVectorLiteral.vector(new float[] {1.0f}));
    }

    @Test
    void usesDecimalPointRegardlessOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // A locale that renders decimals with a comma would otherwise emit
            // "0,1" and silently corrupt the vector.
            Locale.setDefault(Locale.GERMANY);
            String literal = PgVectorLiteral.vector(new float[] {0.5f, 1.5f});

            assertEquals("[0.5,1.5]", literal);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void nullVectorIsNullSoCallerCanSkipTheQuery() {
        assertNull(PgVectorLiteral.vector(null));
    }

    @Test
    void rejectsEmptyVector() {
        assertThrows(IllegalArgumentException.class,
                () -> PgVectorLiteral.vector(new float[0]));
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException.class,
                () -> PgVectorLiteral.vector(new float[] {0.1f, Float.NaN}));
        assertThrows(IllegalArgumentException.class,
                () -> PgVectorLiteral.vector(new float[] {Float.POSITIVE_INFINITY}));
    }

    @Test
    void rendersEmptyArrayForNoSchemes() {
        assertEquals("{}", PgVectorLiteral.array(List.of()));
        assertEquals("{}", PgVectorLiteral.array(null));
    }

    @Test
    void quotesSchemeNames() {
        assertEquals("{\"TOP_CLASS_PWD\",\"ISHAN_UDAY_SCHOLARSHIP\"}",
                PgVectorLiteral.array(List.of("TOP_CLASS_PWD", "ISHAN_UDAY_SCHOLARSHIP")));
    }

    @Test
    void dropsNullAndBlankSchemeNames() {
        assertEquals("{\"A\"}", PgVectorLiteral.array(java.util.Arrays.asList("A", null, "  ")));
    }
}