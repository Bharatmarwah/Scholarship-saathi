package com.bharat.scholarship_rag_backend.retrieval;

/**
 * Raised when the answer model could not be called. This is an outage, not a
 * finding that the retrieved passages lack the answer.
 */
public class GenerationUnavailableException extends RuntimeException {

    public GenerationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
