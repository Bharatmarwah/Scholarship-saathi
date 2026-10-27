package com.bharat.scholarship_rag_backend.validator;

public enum ValidationMode {
    SPECIFIC_SCHEME,
    SCHOLARSHIP_DISCOVERY,
    /**
     * Two or more schemes were named together, usually a comparison. There is
     * no single scheme whose essential fields can be validated, so no missing
     * information is collected; retrieval is simply scoped to those schemes.
     */
    MULTI_SCHEME
}