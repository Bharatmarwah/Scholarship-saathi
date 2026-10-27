package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;

import java.util.List;

/**
 * How one scheme's criteria evaluated against a profile.
 *
 * @param scheme         the scheme matched
 * @param verdict        overall advisory verdict
 * @param criteria       every criterion's outcome
 * @param failedReasons  labels + detail of the criteria that failed
 * @param missingDetails labels of the criteria that could not be evaluated
 * @param source         the document the rules came from
 */
public record EligibilityAssessment(
        SchemeType scheme,
        EligibilityVerdict verdict,
        List<CriterionResult> criteria,
        List<String> failedReasons,
        List<String> missingDetails,
        String source) {

    public boolean eligible() {
        return verdict == EligibilityVerdict.ELIGIBLE;
    }

    public boolean excluded() {
        return verdict == EligibilityVerdict.NOT_ELIGIBLE;
    }
}
