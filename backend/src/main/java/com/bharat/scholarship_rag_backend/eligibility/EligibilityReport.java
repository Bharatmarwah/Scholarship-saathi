package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;

import java.util.List;

/**
 * Result of matching a profile against several schemes at once, used by the
 * discovery ("which scholarship suits me?") flow. Assessments keep catalog
 * order; the grouped lists are the recommendation the answer is built from.
 */
public record EligibilityReport(List<EligibilityAssessment> assessments) {

    public List<SchemeType> recommended() {
        return assessments.stream()
                .filter(EligibilityAssessment::eligible)
                .map(EligibilityAssessment::scheme)
                .toList();
    }

    public List<SchemeType> possiblyEligible() {
        return assessments.stream()
                .filter(a -> a.verdict() == EligibilityVerdict.POSSIBLY_ELIGIBLE)
                .map(EligibilityAssessment::scheme)
                .toList();
    }

    public List<SchemeType> notEligible() {
        return assessments.stream()
                .filter(EligibilityAssessment::excluded)
                .map(EligibilityAssessment::scheme)
                .toList();
    }

    public boolean hasAnyMatch() {
        return !recommended().isEmpty() || !possiblyEligible().isEmpty();
    }
}
