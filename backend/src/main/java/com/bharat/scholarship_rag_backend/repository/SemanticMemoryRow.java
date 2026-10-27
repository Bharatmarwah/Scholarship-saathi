package com.bharat.scholarship_rag_backend.repository;

/**
 * Row projection for the semantic-memory distance query. Spring Data maps the
 * aliased columns onto these getters, so callers never touch positional
 * {@code Object[]} rows.
 */
public interface SemanticMemoryRow {

    String getContext();

    Double getDistance();
}
