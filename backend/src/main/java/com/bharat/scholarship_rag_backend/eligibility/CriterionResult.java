package com.bharat.scholarship_rag_backend.eligibility;

/**
 * One criterion's evaluation, carrying a stable identifier for the rule, the
 * human label used in answers and a short explanation of the outcome.
 *
 * @param code    stable rule id, e.g. {@code INCOME_CEILING}
 * @param label   student-facing criterion name, e.g. "Annual family income"
 * @param outcome PASS / FAIL / UNKNOWN / NOT_APPLICABLE
 * @param detail  short explanation, e.g. "₹8,00,000 must not exceed ₹8,00,000"
 */
public record CriterionResult(
        String code,
        String label,
        CriterionOutcome outcome,
        String detail) {

    public boolean failed() {
        return outcome == CriterionOutcome.FAIL;
    }

    public boolean unknown() {
        return outcome == CriterionOutcome.UNKNOWN;
    }
}
