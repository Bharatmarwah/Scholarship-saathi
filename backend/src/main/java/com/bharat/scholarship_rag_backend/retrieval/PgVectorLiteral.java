package com.bharat.scholarship_rag_backend.retrieval;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Renders Java values in the literal syntax pgvector expects inside a query.
 *
 * <p>Native repository methods cannot bind a {@code float[]} into
 * {@code CAST(? AS vector)}: PostgreSQL has no {@code float8[] -> vector} cast,
 * so the call fails at runtime. The accepted form is a text literal such as
 * {@code "[0.1,0.2]"}. Same story for the scheme filter, which is passed as
 * {@code "{SCHEME_A,SCHEME_B}"}.
 *
 * <p>Locale is pinned to {@link Locale#ROOT} on purpose. A default locale with
 * a comma decimal separator would emit {@code "0,1"} and corrupt the vector.
 */
public final class PgVectorLiteral {

    private PgVectorLiteral() {
    }

    /**
     * @param vector embedding to render
     * @return pgvector literal, or {@code null} when the vector is null
     * @throws IllegalArgumentException if the vector is empty or holds a
     *                                  non-finite value, either of which would
     *                                  otherwise reach the database as a
     *                                  malformed literal
     */
    public static String vector(float[] vector) {
        if (vector == null) {
            return null;
        }
        if (vector.length == 0) {
            throw new IllegalArgumentException("Cannot render an empty embedding");
        }
        StringBuilder literal = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Embedding contains a non-finite value at index " + i);
            }
            literal.append(String.format(Locale.ROOT, "%s", value));
        }
        return literal.append(']').toString();
    }

    /**
     * @param values enum names to render
     * @return PostgreSQL array literal; {@code "{}"} when nothing is given
     */
    public static String array(Iterable<String> values) {
        if (values == null) {
            return "{}";
        }
        String joined = java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(","));
        return "{" + joined + "}";
    }
}