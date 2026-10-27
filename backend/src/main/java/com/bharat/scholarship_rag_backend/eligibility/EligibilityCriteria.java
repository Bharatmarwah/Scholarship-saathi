package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;

import java.math.BigDecimal;
import java.util.Set;

/**
 * The "target candidate" profile for one scheme: the concrete eligibility
 * rules transcribed from that scheme's official documents.
 *
 * <p>This is deliberately separate from
 * {@code com.bharat.scholarship_rag_backend.scheme.SchemeDefinition}, which is
 * field-only (which facts to collect). This record holds the actual thresholds
 * the validator is forbidden to encode.
 *
 * <p>An empty collection or a {@code null} numeric means "no constraint on
 * this axis", so a criterion that a scheme does not care about is simply not
 * evaluated.
 *
 * @param scheme                   the scheme these rules belong to
 * @param eligibleSocialCategories allowed categories; empty = any
 * @param incomeCeiling            annual family income ceiling in rupees; null = none
 * @param incomeCeilingInclusive   {@code true} when the scheme says "up to" the
 *                                 ceiling, {@code false} when it says the income
 *                                 must be strictly below it ("less than",
 *                                 "below"). Getting this wrong lets a student
 *                                 sitting exactly on the ceiling through as
 *                                 eligible when the document says otherwise.
 * @param stages                   targeted education stages; empty = any
 * @param minClass                 lowest eligible school class; null = n/a
 * @param maxClass                 highest eligible school class; null = n/a
 * @param eligibleDomicileStates   required domicile states/regions; empty = any
 * @param disabilityRequired       disability is a condition of eligibility
 * @param minDisabilityPercentage  minimum benchmark disability percentage; null = none
 * @param indianNationalityRequired the scheme is open only to Indian nationals
 * @param firstYearOnly            fresh awards are restricted to first-year
 *                                 entrants; renewals may continue in later
 *                                 years when the application type is known
 * @param regularModeRequired      only regular/full-time study is eligible
 * @param priorDegreeDisallowed    a prior degree makes the candidate ineligible
 * @param source                   the document the rules were transcribed from
 */
public record EligibilityCriteria(
        SchemeType scheme,
        Set<String> eligibleSocialCategories,
        BigDecimal incomeCeiling,
        boolean incomeCeilingInclusive,
        Set<EducationStage> stages,
        Integer minClass,
        Integer maxClass,
        Set<String> eligibleDomicileStates,
        boolean disabilityRequired,
        Integer minDisabilityPercentage,
        boolean indianNationalityRequired,
        boolean firstYearOnly,
        boolean regularModeRequired,
        boolean priorDegreeDisallowed,
        String source) {
}
