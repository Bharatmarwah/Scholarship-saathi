package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.validator.ValidationMode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns an {@link EligibilityReport} into the student-facing answer.
 *
 * <p>Wording is intentionally advisory: it states what the shared details
 * imply and always points the student to the National Scholarship Portal for
 * the authoritative decision, because eligibility is finally decided by the
 * awarding body.
 */
@Component
public class EligibilityAnswerFormatter {

    private static final String VERIFY =
            "Eligibility is finally decided by the awarding body; please verify "
                    + "the latest guidelines and your documents on the National "
                    + "Scholarship Portal (NSP) before applying.";

    private static final Map<SchemeType, String> DISPLAY_NAMES = Map.of(
            SchemeType.PM_USP_CSSS, "the Central Sector Scheme of Scholarship for College and University Students (PM-USP CSSS)",
            SchemeType.ISHAN_UDAY, "the Ishan Uday Special Scholarship Scheme for the North East",
            SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, "PM YASASVI Top Class Schools",
            SchemeType.TOP_CLASS_SC, "Top Class Education for SC Students",
            SchemeType.TOP_CLASS_PWD, "Top Class Education for Students with Disabilities");

    public String displayName(SchemeType scheme) {
        return DISPLAY_NAMES.getOrDefault(scheme, scheme.name().replace('_', ' '));
    }

    public String format(ValidationMode mode, EligibilityReport report) {
        if (mode == ValidationMode.SCHOLARSHIP_DISCOVERY) {
            return formatDiscovery(report);
        }
        List<EligibilityAssessment> assessments = report.assessments();
        if (assessments.size() == 1) {
            return formatSingle(assessments.getFirst());
        }
        return formatComparison(assessments);
    }

    public String formatSingle(EligibilityAssessment assessment) {
        String name = displayName(assessment.scheme());
        return switch (assessment.verdict()) {
            case ELIGIBLE -> "Based on the details you shared, you appear to meet the "
                    + "eligibility conditions for " + name + ". " + VERIFY;
            case NOT_ELIGIBLE -> "Based on the details you shared, you do not appear to be "
                    + "eligible for " + name + " because "
                    + reasons(assessment) + ". " + VERIFY;
            case POSSIBLY_ELIGIBLE, UNKNOWN -> "I can check whether you are eligible for "
                    + name + ", but I still need: " + missing(assessment) + ". "
                    + "Could you share " + (missing(assessment).contains(",")
                            ? "those details" : "that detail") + "?";
        };
    }

    private String formatComparison(List<EligibilityAssessment> assessments) {
        String eligible = joinNames(assessments.stream()
                .filter(EligibilityAssessment::eligible).map(EligibilityAssessment::scheme).toList());
        String notEligible = joinNames(assessments.stream()
                .filter(EligibilityAssessment::excluded).map(EligibilityAssessment::scheme).toList());

        StringBuilder answer = new StringBuilder("Here is how the schemes compare for the details you shared:\n");
        if (!eligible.isEmpty()) {
            answer.append("- You appear to meet the conditions for ").append(eligible).append(".\n");
        }
        if (!notEligible.isEmpty()) {
            answer.append("- You do not appear to meet the conditions for ").append(notEligible).append(".\n");
        }
        if (eligible.isEmpty() && notEligible.isEmpty()) {
            answer.append("- I need a few more details before I can compare them.\n");
        }
        answer.append(VERIFY);
        return answer.toString();
    }

    public String formatDiscovery(EligibilityReport report) {
        List<SchemeType> recommended = report.recommended();
        if (!recommended.isEmpty()) {
            return "Based on the details you shared, these scholarships appear to match you: "
                    + joinNames(recommended) + ". " + VERIFY;
        }

        List<SchemeType> possibly = report.possiblyEligible();
        if (!possibly.isEmpty()) {
            Set<String> missing = report.assessments().stream()
                    .filter(a -> a.verdict() == EligibilityVerdict.POSSIBLY_ELIGIBLE)
                    .flatMap(a -> a.missingDetails().stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return "You may qualify for " + joinNames(possibly)
                    + ", but I need a few more details to be sure: "
                    + String.join(", ", missing) + ". Could you share those?";
        }

        // Generic fallback: nothing matched yet, so explain the general
        // conditions rather than refusing outright.
        return "I could not confirm a matching scholarship from the details shared so far. "
                + "The supported schemes and their main conditions are:\n"
                + "• PM-USP CSSS (college/university): regular undergraduate study and family income up to Rs 4.5 lakh.\n"
                + "• Ishan Uday (North East): North-Eastern domicile, first undergraduate degree, parental income below Rs 4.5 lakh.\n"
                + "• PM YASASVI Top Class Schools: OBC/EBC/DNT students of classes 9-12 with household income under Rs 2.5 lakh.\n"
                + "• Top Class Education for SC Students: SC students in regular first-year study with family income up to Rs 8 lakh.\n"
                + "• Top Class Education for Students with Disabilities: benchmark disability (40%+) with family income up to Rs 8 lakh.\n"
                + "Tell me your education level, category, family income and any disability, and I can check which ones fit. "
                + VERIFY;
    }

    private static String reasons(EligibilityAssessment assessment) {
        if (assessment.failedReasons().isEmpty()) {
            return "one or more conditions are not met";
        }
        return String.join("; ", assessment.failedReasons());
    }

    private static String missing(EligibilityAssessment assessment) {
        if (assessment.missingDetails().isEmpty()) {
            return "a few more details";
        }
        return String.join(", ", assessment.missingDetails());
    }

    private String joinNames(List<SchemeType> schemes) {
        return schemes.stream().map(this::displayName).collect(Collectors.joining(", "));
    }
}
