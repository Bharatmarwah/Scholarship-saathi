package com.bharat.scholarship_rag_backend.retrieval;

/**
 * Raised when every retrieval arm failed, so an empty result cannot be
 * interpreted as evidence that the corpus lacks the answer.
 */
public class RetrievalUnavailableException extends RuntimeException {

    public RetrievalUnavailableException(String message) {
        super(message);
    }
}
