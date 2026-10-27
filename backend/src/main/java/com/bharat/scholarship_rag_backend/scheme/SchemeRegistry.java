package com.bharat.scholarship_rag_backend.scheme;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central catalog of the exactly five supported scholarship schemes and the
 * deterministic resolution of a raw (possibly LLM-produced) scheme name.
 *
 * <p>Definitions come from the supplied official scheme documents. No numeric
 * eligibility thresholds are stored here.
 */
@Component
public class SchemeRegistry {

    private static final Map<SchemeType, SchemeDefinition> DEFINITIONS = buildDefinitions();

    private static final Map<String, SchemeType> ALIASES = buildAliases();

    private static final List<SchemeType> ALL = List.of(
            SchemeType.PM_USP_CSSS,
            SchemeType.ISHAN_UDAY,
            SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS,
            SchemeType.TOP_CLASS_SC,
            SchemeType.TOP_CLASS_PWD
    );

    /**
     * Cross-scheme screening profile: the fields collected progressively
     * during discovery, in conversational order. This is NOT a set of fields
     * essential for every scheme. Each {@link SchemeDefinition} decides which
     * of these (and its own additional fields) actually matter. Year/class
     * are mutually exclusive by education stage and are handled by the
     * validation engine's applicability skip.
     */
    private static final List<StudentProfileField> DISCOVERY_ORDER = List.of(
            StudentProfileField.EDUCATION_LEVEL,
            StudentProfileField.CURRENT_YEAR,
            StudentProfileField.CURRENT_CLASS,
            StudentProfileField.SOCIAL_CATEGORY,
            StudentProfileField.ANNUAL_FAMILY_INCOME,
            StudentProfileField.COURSE,
            StudentProfileField.DOMICILE_STATE,
            StudentProfileField.HAS_DISABILITY
    );

    public List<StudentProfileField> discoveryOrder() {
        return DISCOVERY_ORDER;
    }

    public Optional<SchemeDefinition> find(SchemeType scheme) {
        return scheme == null ? Optional.empty() : Optional.ofNullable(DEFINITIONS.get(scheme));
    }

    public SchemeDefinition definition(SchemeType scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("Unknown scheme: null");
        }
        SchemeDefinition definition = DEFINITIONS.get(scheme);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown scheme: " + scheme);
        }
        return definition;
    }

    public List<SchemeType> allSchemes() {
        return ALL;
    }

    /**
     * Normalizes the raw scheme reference and maps it to a known scheme.
     * Returns empty unless exactly one scheme is identified; it never guesses.
     *
     * <p>A reference that names several schemes is deliberately not accepted
     * here. Use {@link #resolveAll(String)} for those.
     */
    public Optional<SchemeType> resolve(String rawScheme) {
        List<SchemeType> resolved = resolveAll(rawScheme);
        return resolved.size() == 1 ? Optional.of(resolved.getFirst()) : Optional.empty();
    }

    /**
     * Resolves every scheme named in a raw reference, so that a comparison
     * such as "YASASVI and Ishan Uday" keeps both schemes instead of one.
     *
     * <p>Parts are split on commas, "and" and "&amp;". Each part is resolved on
     * its own and unknown parts are dropped rather than guessed. Duplicates
     * collapse. The result follows registry order so callers get a stable
     * list.
     *
     * @return the schemes actually named, possibly empty
     */
    public List<SchemeType> resolveAll(String rawScheme) {
        if (rawScheme == null || rawScheme.isBlank()) {
            return List.of();
        }
        List<String> words = words(rawScheme);
        if (words.isEmpty()) {
            return List.of();
        }

        // Aliases are matched longest first so that a specific reference wins
        // over a shorter alias it contains: "topclasssc" must not be split
        // into "topclass" leftovers, and "pmuspcsss" must beat "pmusp".
        // An alias may span several words ("top class sc"), but it must cover
        // whole words. This prevents "topclasssc" from matching inside the
        // unrelated display name "Top Class School".
        Set<SchemeType> matched = new LinkedHashSet<>();
        boolean[] consumed = new boolean[words.size()];

        for (String alias : sortedAliasesByLengthDesc()) {
            SchemeType scheme = ALIASES.get(alias);
            for (int start = 0; start < words.size(); start++) {
                if (consumed[start]) {
                    continue;
                }
                int end = matchEnd(words, consumed, start, alias);
                if (end > start) {
                    matched.add(scheme);
                    for (int i = start; i < end; i++) {
                        consumed[i] = true;
                    }
                    start = end - 1;
                }
            }
        }
        return ALL.stream().filter(matched::contains).toList();
    }

    /**
     * Returns the exclusive end word index when the alias is exactly the
     * concatenation of consecutive unconsumed words starting at {@code start},
     * or {@code start} when it is not.
     */
    private static int matchEnd(
            List<String> words,
            boolean[] consumed,
            int start,
            String alias) {
        StringBuilder candidate = new StringBuilder();
        for (int end = start; end < words.size() && !consumed[end]; end++) {
            candidate.append(words.get(end));
            if (candidate.length() > alias.length()) {
                return start;
            }
            if (candidate.length() == alias.length()) {
                return candidate.toString().equals(alias) ? end + 1 : start;
            }
        }
        return start;
    }

    private static List<String> sortedAliasesByLengthDesc() {
        return ALIASES.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static List<String> words(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.isEmpty() ? List.of() : List.of(normalized.split(" +"));
    }

    /**
     * Order-preserving set so that essential/discovery field ordering is
     * stable and validator missingFields lists are deterministic.
     */
    private static Set<StudentProfileField> ordered(StudentProfileField... fields) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(fields)));
    }

    private static Map<String, SchemeType> buildAliases() {
        return Map.ofEntries(
                Map.entry("pmusp", SchemeType.PM_USP_CSSS),
                Map.entry("pmuspcsss", SchemeType.PM_USP_CSSS),
                Map.entry("pmcsss", SchemeType.PM_USP_CSSS),
                Map.entry("pmcssusp", SchemeType.PM_USP_CSSS),
                Map.entry("pmcssus", SchemeType.PM_USP_CSSS),
                Map.entry("cssusp", SchemeType.PM_USP_CSSS),
                Map.entry("postmatric", SchemeType.PM_USP_CSSS),
                Map.entry("postmatricscholarship", SchemeType.PM_USP_CSSS),
                Map.entry("csss", SchemeType.PM_USP_CSSS),
                Map.entry("ishanuday", SchemeType.ISHAN_UDAY),
                Map.entry("ishan", SchemeType.ISHAN_UDAY),
                Map.entry("pmyasasvi", SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                Map.entry("yasasvi", SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                Map.entry("topclassschool", SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                Map.entry("topclassschools", SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                Map.entry("topclasssc", SchemeType.TOP_CLASS_SC),
                // Full official and catalogue display names. A shorter alias
                // "topclasseducationforstudents" used to match inside the PWD
                // name and silently resolve a PWD question to the SC scheme, so
                // each complete name is spelled out and matched on word
                // boundaries.
                Map.entry("topclasseducationsc", SchemeType.TOP_CLASS_SC),
                Map.entry("topclasseducationforscstudents", SchemeType.TOP_CLASS_SC),
                Map.entry("topclasspwd", SchemeType.TOP_CLASS_PWD),
                Map.entry("pwd", SchemeType.TOP_CLASS_PWD),
                Map.entry("topclasseducationpwd", SchemeType.TOP_CLASS_PWD),
                Map.entry("topclassdisabilities", SchemeType.TOP_CLASS_PWD),
                Map.entry("topclassstudentswithdisabilities", SchemeType.TOP_CLASS_PWD),
                Map.entry("topclasseducationforstudentswithdisabilities", SchemeType.TOP_CLASS_PWD)
        );
    }

    private static Map<SchemeType, SchemeDefinition> buildDefinitions() {
        return Map.ofEntries(
                Map.entry(SchemeType.PM_USP_CSSS, new SchemeDefinition(
                        SchemeType.PM_USP_CSSS,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.ANNUAL_FAMILY_INCOME),
                        ordered(StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.CLASS12_PERCENTILE,
                                StudentProfileField.BOARD,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.APPLICATION_TYPE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                        ordered()
                )),
                Map.entry(SchemeType.ISHAN_UDAY, new SchemeDefinition(
                        SchemeType.ISHAN_UDAY,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.DOMICILE_STATE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.COURSE),
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.DOMICILE_STATE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.HAS_PRIOR_DEGREE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                        ordered(StudentProfileField.COURSE_TYPE),
                        ordered()
                )),
                Map.entry(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, new SchemeDefinition(
                        SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS,
                        ordered(StudentProfileField.CURRENT_CLASS,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME),
                        ordered(StudentProfileField.CURRENT_CLASS,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.INSTITUTION_TYPE,
                                StudentProfileField.PREVIOUS_CLASS_MARKS),
                        ordered(StudentProfileField.PREVIOUS_CLASS_MARKS)
                )),
                Map.entry(SchemeType.TOP_CLASS_SC, new SchemeDefinition(
                        SchemeType.TOP_CLASS_SC,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME),
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.APPLICATION_TYPE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP,
                                StudentProfileField.SIBLINGS_RECEIVING_BENEFIT),
                        ordered(StudentProfileField.ADMISSION_RANK)
                )),
                Map.entry(SchemeType.TOP_CLASS_PWD, new SchemeDefinition(
                        SchemeType.TOP_CLASS_PWD,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.HAS_DISABILITY),
                        ordered(StudentProfileField.NATIONALITY,
                                StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.HAS_DISABILITY,
                                StudentProfileField.DISABILITY_PERCENTAGE,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.HAS_VALID_DISABILITY_CERTIFICATE,
                                StudentProfileField.HAS_UDID_OR_UDID_ENROLLMENT,
                                StudentProfileField.COURSE_TYPE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                        ordered(StudentProfileField.DISABILITY_PERCENTAGE)
                ))
        );
    }

    public List<String> normalizedAliasKeys() {
        return ALIASES.keySet().stream().sorted().collect(Collectors.toList());
    }
}