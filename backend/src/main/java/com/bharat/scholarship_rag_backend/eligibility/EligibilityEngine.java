package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic matcher between a {@link StudentProfileDto} and each scheme's
 * {@link EligibilityCriteria}. This is the "target candidate" check the
 * validator is explicitly not allowed to make: the validator decides only
 * whether enough is known, this decides whether the known values qualify.
 *
 * <p>Every criterion is evaluated independently and reports PASS / FAIL /
 * UNKNOWN. A single FAIL makes the scheme {@code NOT_ELIGIBLE}; if nothing
 * fails but something is unknown the verdict is {@code POSSIBLY_ELIGIBLE}.
 */
@Component
public class EligibilityEngine {

    private final SchemeEligibilityCatalog catalog;

    public EligibilityEngine(SchemeEligibilityCatalog catalog) {
        this.catalog = catalog;
    }

    public EligibilityAssessment assess(StudentProfileDto profile, SchemeType scheme) {
        return assess(profile, catalog.criteria(scheme));
    }

    public EligibilityReport assessAll(StudentProfileDto profile) {
        List<SchemeType> schemes = catalog.all().stream()
                .map(EligibilityCriteria::scheme)
                .toList();
        return assessAll(profile, schemes);
    }

    public EligibilityReport assessAll(StudentProfileDto profile, List<SchemeType> schemes) {
        List<EligibilityAssessment> assessments = new ArrayList<>();
        for (SchemeType scheme : schemes) {
            assessments.add(assess(profile, scheme));
        }
        return new EligibilityReport(List.copyOf(assessments));
    }

    public EligibilityAssessment assess(StudentProfileDto profile, EligibilityCriteria criteria) {
        List<CriterionResult> results = new ArrayList<>();

        evaluateCategories(profile, criteria, results);
        evaluateIncome(profile, criteria, results);
        evaluateStage(profile, criteria, results);
        evaluateClassRange(profile, criteria, results);
        evaluateDomicile(profile, criteria, results);
        evaluateDisability(profile, criteria, results);
        evaluateNationality(profile, criteria, results);
        evaluateApplicationType(profile, criteria, results);
        evaluateFirstYear(profile, criteria, results);
        evaluateRegularMode(profile, criteria, results);
        evaluatePriorDegree(profile, criteria, results);

        List<String> failed = results.stream()
                .filter(CriterionResult::failed)
                .map(r -> r.label() + " (" + r.detail() + ")")
                .toList();
        List<String> unknown = results.stream()
                .filter(CriterionResult::unknown)
                .map(CriterionResult::label)
                .toList();

        return new EligibilityAssessment(
                criteria.scheme(),
                verdictOf(results),
                List.copyOf(results),
                failed,
                unknown,
                criteria.source());
    }

    private EligibilityVerdict verdictOf(List<CriterionResult> results) {
        if (results.stream().anyMatch(CriterionResult::failed)) {
            return EligibilityVerdict.NOT_ELIGIBLE;
        }
        List<CriterionResult> applicable = results.stream()
                .filter(r -> r.outcome() != CriterionOutcome.NOT_APPLICABLE)
                .toList();
        if (applicable.isEmpty()) {
            return EligibilityVerdict.UNKNOWN;
        }
        boolean anyUnknown = applicable.stream().anyMatch(CriterionResult::unknown);
        if (!anyUnknown) {
            return EligibilityVerdict.ELIGIBLE;
        }
        boolean anyPass = applicable.stream()
                .anyMatch(r -> r.outcome() == CriterionOutcome.PASS);
        return anyPass ? EligibilityVerdict.POSSIBLY_ELIGIBLE : EligibilityVerdict.UNKNOWN;
    }

    private void evaluateCategories(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        Set<String> allowed = c.eligibleSocialCategories();
        if (allowed == null || allowed.isEmpty()) {
            out.add(na("SOCIAL_CATEGORY", "Social category"));
            return;
        }
        String value = profile.getSocialCategory();
        if (blank(value)) {
            out.add(new CriterionResult("SOCIAL_CATEGORY", "Social category",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        String canonical = canonicalCategory(value);
        if (canonical != null && allowed.stream().anyMatch(a -> normalize(a).equals(normalize(canonical)))) {
            out.add(pass("SOCIAL_CATEGORY", "Social category", value + " is covered"));
        } else {
            out.add(new CriterionResult("SOCIAL_CATEGORY", "Social category",
                    CriterionOutcome.FAIL, "only " + String.join("/", allowed) + " are eligible"));
        }
    }

    private void evaluateIncome(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        BigDecimal ceiling = c.incomeCeiling();
        if (ceiling == null) {
            out.add(na("INCOME_CEILING", "Annual family income"));
            return;
        }
        BigDecimal income = profile.getAnnualFamilyIncome();
        if (income == null) {
            out.add(new CriterionResult("INCOME_CEILING", "Annual family income",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        int comparison = income.compareTo(ceiling);
        boolean inclusive = c.incomeCeilingInclusive();
        if (comparison < 0 || (inclusive && comparison == 0)) {
            out.add(pass("INCOME_CEILING", "Annual family income",
                    (inclusive ? "within " : "below ") + format(ceiling)));
        } else {
            out.add(new CriterionResult("INCOME_CEILING", "Annual family income",
                    CriterionOutcome.FAIL,
                    (inclusive ? "must not exceed " : "must be below ") + format(ceiling)));
        }
    }

    private void evaluateStage(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        Set<EducationStage> stages = c.stages();
        if (stages == null || stages.isEmpty()) {
            out.add(na("EDUCATION_STAGE", "Education level"));
            return;
        }
        var stage = EducationStage.detect(profile.getEducationLevel());
        if (stage.isEmpty()) {
            out.add(new CriterionResult("EDUCATION_STAGE", "Education level",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        if (stages.contains(stage.get())) {
            out.add(pass("EDUCATION_STAGE", "Education level", profile.getEducationLevel()));
        } else {
            out.add(new CriterionResult("EDUCATION_STAGE", "Education level",
                    CriterionOutcome.FAIL, "scheme targets " + joinStages(stages)));
        }
    }

    private void evaluateClassRange(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        Integer min = c.minClass();
        Integer max = c.maxClass();
        if (min == null && max == null) {
            out.add(na("CLASS_RANGE", "Class"));
            return;
        }
        Integer currentClass = profile.getCurrentClass();
        if (currentClass == null) {
            out.add(new CriterionResult("CLASS_RANGE", "Class",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        boolean ok = (min == null || currentClass >= min) && (max == null || currentClass <= max);
        String range = (min == null ? "up to " : min + "-") + (max == null ? "and above" : max);
        if (ok) {
            out.add(pass("CLASS_RANGE", "Class", "class " + currentClass + " is in range"));
        } else {
            out.add(new CriterionResult("CLASS_RANGE", "Class",
                    CriterionOutcome.FAIL, "only classes " + range + " are eligible"));
        }
    }

    private void evaluateDomicile(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        Set<String> states = c.eligibleDomicileStates();
        if (states == null || states.isEmpty()) {
            out.add(na("DOMICILE", "Domicile"));
            return;
        }
        String value = profile.getDomicileState();
        if (blank(value)) {
            out.add(new CriterionResult("DOMICILE", "Domicile",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        boolean ok = states.stream().anyMatch(normalized::contains);
        if (ok) {
            out.add(pass("DOMICILE", "Domicile", value));
        } else {
            out.add(new CriterionResult("DOMICILE", "Domicile",
                    CriterionOutcome.FAIL,
                    "domicile must be in one of: " + String.join(", ", states)));
        }
    }

    private void evaluateDisability(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        if (c.disabilityRequired()) {
            Boolean has = profile.getHasDisability();
            if (has == null) {
                out.add(new CriterionResult("DISABILITY", "Disability",
                        CriterionOutcome.UNKNOWN, "not provided"));
            } else if (has) {
                out.add(pass("DISABILITY", "Disability", "reported"));
            } else {
                out.add(new CriterionResult("DISABILITY", "Disability",
                        CriterionOutcome.FAIL, "scheme is only for students with disabilities"));
            }
        } else {
            out.add(na("DISABILITY", "Disability"));
        }

        Integer min = c.minDisabilityPercentage();
        if (min == null) {
            out.add(na("DISABILITY_PERCENTAGE", "Disability percentage"));
            return;
        }
        Integer percentage = profile.getDisabilityPercentage();
        if (percentage == null) {
            out.add(new CriterionResult("DISABILITY_PERCENTAGE", "Disability percentage",
                    CriterionOutcome.UNKNOWN, "not provided"));
        } else if (percentage >= min) {
            out.add(pass("DISABILITY_PERCENTAGE", "Disability percentage", percentage + "%"));
        } else {
            out.add(new CriterionResult("DISABILITY_PERCENTAGE", "Disability percentage",
                    CriterionOutcome.FAIL, "benchmark disability of " + min + "% or more is required"));
        }
    }

    private void evaluateNationality(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        if (!c.indianNationalityRequired()) {
            out.add(na("NATIONALITY", "Nationality"));
            return;
        }
        String value = profile.getNationality();
        if (blank(value)) {
            out.add(new CriterionResult("NATIONALITY", "Nationality",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("indian") || normalized.equals("india")) {
            out.add(pass("NATIONALITY", "Nationality", value));
        } else {
            out.add(new CriterionResult("NATIONALITY", "Nationality",
                    CriterionOutcome.FAIL, "only Indian nationals are eligible"));
        }
    }

    /**
     * The fresh/renewal distinction is required only for schemes whose fresh
     * award is limited to first-year entrants. A first-year student is treated
     * as fresh without asking; for later years the application type decides
     * whether the first-year restriction applies. When it is not known, the
     * criterion stays UNKNOWN instead of rejecting a renewal as if it were a
     * fresh application.
     */
    private void evaluateApplicationType(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        if (!c.firstYearOnly()) {
            out.add(na("APPLICATION_TYPE", "Application type"));
            return;
        }
        Integer year = profile.getCurrentYear();
        if (year != null && year == 1) {
            out.add(na("APPLICATION_TYPE", "Application type"));
            return;
        }
        String raw = profile.getApplicationType();
        if (blank(raw)) {
            out.add(new CriterionResult("APPLICATION_TYPE", "Application type",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        String canonical = canonicalApplicationType(raw);
        if (canonical == null) {
            out.add(new CriterionResult("APPLICATION_TYPE", "Application type",
                    CriterionOutcome.UNKNOWN, "unrecognized value: " + raw));
            return;
        }
        out.add(pass("APPLICATION_TYPE", "Application type", canonical.toLowerCase(Locale.ROOT)));
    }

    private void evaluateFirstYear(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        if (!c.firstYearOnly()) {
            out.add(na("FIRST_YEAR", "Year of study"));
            return;
        }
        Integer year = profile.getCurrentYear();
        if (year == null) {
            out.add(new CriterionResult("FIRST_YEAR", "Year of study",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        if (year == 1) {
            out.add(pass("FIRST_YEAR", "Year of study", "first-year entrant"));
            return;
        }

        String applicationType = canonicalApplicationType(profile.getApplicationType());
        if ("RENEWAL".equals(applicationType)) {
            out.add(pass("FIRST_YEAR", "Year of study", "renewal in year " + year));
        } else if ("FRESH".equals(applicationType)) {
            out.add(new CriterionResult("FIRST_YEAR", "Year of study",
                    CriterionOutcome.FAIL, "fresh scholarships are only for first-year entrants"));
        } else {
            // The missing application type is already reported by
            // evaluateApplicationType; do not double-penalize or guess.
            out.add(na("FIRST_YEAR", "Year of study"));
        }
    }

    private static String canonicalApplicationType(String raw) {
        if (blank(raw)) {
            return null;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.contains("renew")) {
            return "RENEWAL";
        }
        if (value.contains("fresh") || value.contains("first time")
                || value.contains("firsttime") || value.contains("new application")) {
            return "FRESH";
        }
        return null;
    }

    private void evaluateRegularMode(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        if (!c.regularModeRequired()) {
            out.add(na("REGULAR_MODE", "Mode of study"));
            return;
        }
        Boolean regular = profile.getRegularMode();
        if (regular == null) {
            out.add(new CriterionResult("REGULAR_MODE", "Mode of study",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        if (regular) {
            out.add(pass("REGULAR_MODE", "Mode of study", "regular/full-time"));
        } else {
            out.add(new CriterionResult("REGULAR_MODE", "Mode of study",
                    CriterionOutcome.FAIL, "only regular/full-time study is eligible"));
        }
    }

    private void evaluatePriorDegree(StudentProfileDto profile, EligibilityCriteria c, List<CriterionResult> out) {
        if (!c.priorDegreeDisallowed()) {
            out.add(na("PRIOR_DEGREE", "Prior degree"));
            return;
        }
        Boolean completed = profile.getCompletedUGDegree();
        if (completed == null) {
            out.add(new CriterionResult("PRIOR_DEGREE", "Prior degree",
                    CriterionOutcome.UNKNOWN, "not provided"));
            return;
        }
        if (!completed) {
            out.add(pass("PRIOR_DEGREE", "Prior degree", "no prior degree"));
        } else {
            out.add(new CriterionResult("PRIOR_DEGREE", "Prior degree",
                    CriterionOutcome.FAIL, "students who already hold a degree are not eligible"));
        }
    }

    private static CriterionResult pass(String code, String label, String detail) {
        return new CriterionResult(code, label, CriterionOutcome.PASS, detail);
    }

    private static CriterionResult na(String code, String label) {
        return new CriterionResult(code, label, CriterionOutcome.NOT_APPLICABLE, "not required");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Maps the many ways a category is written onto the codes the catalog uses
     * (SC, ST, OBC, EBC, DNT, GEN), or {@code null} when unrecognised.
     */
    private static String canonicalCategory(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.contains("scheduled caste") || value.contains("(sc)") || value.trim().equals("sc")) {
            return "SC";
        }
        if (value.contains("scheduled tribe") || value.contains("(st)") || value.trim().equals("st")) {
            return "ST";
        }
        if (value.contains("obc")) {
            return "OBC";
        }
        if (value.contains("ebc")) {
            return "EBC";
        }
        if (value.contains("dnt")) {
            return "DNT";
        }
        if (value.contains("general") || value.trim().equals("ur") || value.contains("unreserved")) {
            return "GEN";
        }
        return null;
    }

    private static String format(BigDecimal amount) {
        BigDecimal lakh = amount.divide(BigDecimal.valueOf(100000)).stripTrailingZeros();
        return "Rs " + lakh.toPlainString() + " lakh";
    }

    private static String joinStages(Set<EducationStage> stages) {
        return stages.stream().map(EligibilityEngine::stageLabel).reduce((a, b) -> a + " or " + b).orElse("");
    }

    private static String stageLabel(EducationStage stage) {
        return switch (stage) {
            case SCHOOL -> "school (class 9-12)";
            case UNDERGRADUATE -> "undergraduate";
            case POSTGRADUATE -> "postgraduate";
        };
    }
}
