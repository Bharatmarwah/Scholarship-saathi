package com.bharat.scholarship_rag_backend.eligibility;

/**
 * Result of evaluating a single eligibility criterion against a profile.
 */
public enum CriterionOutcome {
    PASS,
    FAIL,
    UNKNOWN,
    NOT_APPLICABLE
}
