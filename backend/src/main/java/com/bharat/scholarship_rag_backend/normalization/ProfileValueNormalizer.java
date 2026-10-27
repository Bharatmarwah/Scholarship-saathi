package com.bharat.scholarship_rag_backend.normalization;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based half of the hybrid profile pipeline. The LLM
 * composer extracts what the user said; this normalizer canonicalizes it so a
 * single stored value covers every phrasing of the same fact.
 *
 * <p>Design rule: <strong>never reject for an unknown value.</strong> A
 * recognized value is mapped to a canonical token; anything unrecognized is
 * returned cleaned (trimmed) rather than dropped, so the student is never
 * trapped in a re-ask loop. Detection ({@link #detectSocialCategory}) is the
 * only method that returns empty, because it is used to backfill a value the
 * LLM missed from their raw words.
 */
@Component
public class ProfileValueNormalizer {

    private static final Set<String> GENERAL_TOKENS = Set.of("gen", "ur", "oc", "open", "unreserved");
    private static final Set<String> OBC_TOKENS = Set.of("obc", "bc", "mbc", "obcncl", "otherbc");
    private static final Set<String> SC_TOKENS = Set.of("sc", "scheduledcaste", "schedulecaste");
    private static final Set<String> ST_TOKENS = Set.of("st", "scheduledtribe", "scheduletribe");
    private static final Set<String> EBC_TOKENS = Set.of("ebc");
    private static final Set<String> EWS_TOKENS = Set.of("ews");
    private static final Set<String> DNT_TOKENS = Set.of("dnt", "nt", "vj", "vjnt", "ntdnt");

    /**
     * Tokens safe to auto-detect inside arbitrary free text. Short ambiguous
     * forms ("ur", "gen", "bc", "nt") are deliberately excluded so a sentence
     * like "what is ur income limit" cannot inject a category.
     */
    private static final Set<String> DETECT_TOKENS = Set.of("obc", "ebc", "ews", "dnt", "sc", "st");

    private static final Map<String, Integer> YEAR_WORDS = Map.ofEntries(
            Map.entry("first", 1),
            Map.entry("1st", 1),
            Map.entry("1", 1),
            Map.entry("second", 2),
            Map.entry("2nd", 2),
            Map.entry("2", 2),
            Map.entry("third", 3),
            Map.entry("3rd", 3),
            Map.entry("3", 3),
            Map.entry("fourth", 4),
            Map.entry("4th", 4),
            Map.entry("4", 4),
            Map.entry("fifth", 5),
            Map.entry("5th", 5),
            Map.entry("5", 5));

    private static final Pattern CURRENT_YEAR = Pattern.compile(
            "(?:\\bi\\s+am|\\bi['\u2019]m)\\s+(?:currently\\s+)?"
                    + "(?:(?:studying|pursuing|doing)\\s+)?(?:in\\s+)?"
                    + "(?:a\\s+|an\\s+|the\\s+)?"
                    + "(first|second|third|fourth|fifth|1st|2nd|3rd|4th|5th|1|2|3|4|5)"
                    + "[\\s-]+(?:year|yr)\\b"
                    + "|\\b(?:my\\s+)?(?:current\\s+)?year\\s*(?:is|:|=)?\\s*([1-5])\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CURRENT_YEAR_CONTEXT = Pattern.compile(
            "\\b(?:currently\\s+in|studying\\s+in|pursuing|doing)\\s+"
                    + "(?:a\\s+|an\\s+|the\\s+)?"
                    + "(first|second|third|fourth|fifth|1st|2nd|3rd|4th|5th|1|2|3|4|5)"
                    + "[\\s-]+(?:year|yr)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INCOME = Pattern.compile(
            "\\b(?:(?:my|our)\\s+(?:annual\\s+)?(?:family\\s+|household\\s+|parental\\s+)?income"
                    + "|(?:annual\\s+)?(?:family|household|parental)\\s+income)\\b"
                    + "\\s*(?:is|of|:|=|was|around|about|approximately|approx\\.?)?\\s*"
                    + "(?:rs\\.?|inr|\u20b9)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*"
                    + "(lakh|lakhs|lac|lacs|crore|crores|cr|k|thousand)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RANGE_OR_APPROXIMATION = Pattern.compile(
            "\\b(below|less than|under|above|more than|over|around|about|approx\\w*|"
                    + "between|upto|up to|nearly|roughly)\\b|\\d\\s*(to|-|\u2013)\\s*\\d",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COURSE_CONTEXT = Pattern.compile(
            "\\b(studying|pursuing|doing|course|student|enrolled|semester|year)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATED_PRIOR_DEGREE = Pattern.compile(
            "\\b(?:have\\s+not|havent|has\\s+not|hasnt|did\\s+not|didnt|do\\s+not|dont|"
                    + "does\\s+not|doesnt|not|never)\\b"
                    + "(?:\\s+\\w+){0,2}\\s+(?:completed|done|hold|held|obtained|earned|received)\\b"
                    + "(?:\\s+\\w+){0,3}\\s+(?:degree|undergraduate|ug|bachelor|graduation)\\b"
                    + "|\\b(?:no\\s+(?:prior\\s+)?degree|first\\s+degree|1st\\s+degree)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRIOR_DEGREE = Pattern.compile(
            "\\b(?:have|has|already|completed|hold|holds|obtained|earned|received)\\b"
                    + "(?:\\s+\\w+){0,3}\\s+(?:degree|undergraduate|ug|bachelor|graduation)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> STATES = buildStates();

    /**
     * Degree/course spellings mapped to a canonical display form. Keys that
     * contain a space are matched as sub-phrases ("b tech" matches "B.Tech");
     * single-token keys match a whole word only, so "be"/"ma"/"bed" cannot
     * false-positive inside ordinary sentences.
     */
    private static final Map<String, String> COURSE_MAP = buildCourseMap();

    private static Map<String, String> buildCourseMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("b tech", "B.Tech");
        m.put("btech", "B.Tech");
        m.put("m tech", "M.Tech");
        m.put("mtech", "M.Tech");
        m.put("b sc", "BSc");
        m.put("bsc", "BSc");
        m.put("m sc", "MSc");
        m.put("msc", "MSc");
        m.put("b com", "B.Com");
        m.put("bcom", "B.Com");
        m.put("m com", "M.Com");
        m.put("mcom", "M.Com");
        m.put("b arch", "B.Arch");
        m.put("barch", "B.Arch");
        m.put("m arch", "M.Arch");
        m.put("march", "M.Arch");
        m.put("b pharm", "B.Pharm");
        m.put("bpharm", "B.Pharm");
        m.put("m pharm", "M.Pharm");
        m.put("mpharm", "M.Pharm");
        m.put("b ed", "B.Ed");
        m.put("m ed", "M.Ed");
        m.put("bdes", "B.Des");
        m.put("mdes", "M.Des");
        m.put("bca", "BCA");
        m.put("mca", "MCA");
        m.put("bba", "BBA");
        m.put("mba", "MBA");
        m.put("llb", "LLB");
        m.put("mbbs", "MBBS");
        m.put("bds", "BDS");
        m.put("phd", "PhD");
        m.put("diploma", "Diploma");
        m.put("polytechnic", "Polytechnic");
        return m;
    }

    private static Map<String, String> buildStates() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("andhra pradesh", "Andhra Pradesh");
        m.put("arunachal pradesh", "Arunachal Pradesh");
        m.put("assam", "Assam");
        m.put("bihar", "Bihar");
        m.put("chhattisgarh", "Chhattisgarh");
        m.put("goa", "Goa");
        m.put("gujarat", "Gujarat");
        m.put("haryana", "Haryana");
        m.put("himachal pradesh", "Himachal Pradesh");
        m.put("jharkhand", "Jharkhand");
        m.put("karnataka", "Karnataka");
        m.put("kerala", "Kerala");
        m.put("madhya pradesh", "Madhya Pradesh");
        m.put("maharashtra", "Maharashtra");
        m.put("manipur", "Manipur");
        m.put("meghalaya", "Meghalaya");
        m.put("mizoram", "Mizoram");
        m.put("nagaland", "Nagaland");
        m.put("odisha", "Odisha");
        m.put("punjab", "Punjab");
        m.put("rajasthan", "Rajasthan");
        m.put("sikkim", "Sikkim");
        m.put("tamil nadu", "Tamil Nadu");
        m.put("telangana", "Telangana");
        m.put("tripura", "Tripura");
        m.put("uttar pradesh", "Uttar Pradesh");
        m.put("uttarakhand", "Uttarakhand");
        m.put("west bengal", "West Bengal");
        m.put("andaman and nicobar islands", "Andaman and Nicobar Islands");
        m.put("chandigarh", "Chandigarh");
        m.put("dadra and nagar haveli and daman and diu", "Dadra and Nagar Haveli and Daman and Diu");
        m.put("delhi", "Delhi");
        m.put("jammu and kashmir", "Jammu and Kashmir");
        m.put("ladakh", "Ladakh");
        m.put("lakshadweep", "Lakshadweep");
        m.put("puducherry", "Puducherry");
        return Map.copyOf(m);
    }

    /**
     * Canonicalizes a stated social category. Known phrasings and abbreviations
     * collapse to one token; an unrecognized category is kept as-is (cleaned) so
     * it is still stored and never re-asked.
     */
    public String normalizeSocialCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String phrase = phrase(raw);
        String canonical = matchSocialCategory(phrase, false);
        return canonical != null ? canonical : raw.trim();
    }

    /**
     * Scans free text for a social-category mention and returns the canonical
     * token. Empty when nothing recognizable is present. Uses a conservative
     * token set, so it is safe to run against every user message. Used to
     * backfill a category the LLM composer failed to extract.
     */
    public Optional<String> detectSocialCategory(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchSocialCategory(phrase(text), true));
    }

    /**
     * Canonicalizes an education level to a token the validation routing
     * understands (CLASS_9..CLASS_12, UG, PG). Unrecognized levels are returned
     * cleaned, unchanged.
     */
    public String normalizeEducationLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String phrase = phrase(raw);
        String canonical = matchEducationLevel(phrase);
        return canonical != null ? canonical : raw.trim();
    }

    /**
     * Detects an education level in free text, returning empty when nothing
     * recognizable is present. Used to resolve a plain answer onto the
     * EDUCATION_LEVEL field.
     */
    public Optional<String> detectEducationLevel(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchEducationLevel(phrase(text)));
    }

    /**
     * Canonicalizes whether the student is applying for a fresh award or
     * renewing one. This distinction matters because several schemes allow
     * fresh awards only in the first year but allow renewal in later years.
     * Unrecognized text is returned cleaned rather than dropped.
     */
    public String normalizeApplicationType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String canonical = matchApplicationType(phrase(raw));
        return canonical != null ? canonical : raw.trim();
    }

    /**
     * Detects a fresh/renewal answer in a short reply. Used when the assistant
     * has just asked the application-type question.
     */
    public Optional<String> detectApplicationType(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchApplicationType(phrase(text)));
    }

    private String matchApplicationType(String phrase) {
        if (phrase.contains("renew")) {
            return "RENEWAL";
        }
        if (phrase.contains("fresh") || phrase.contains("first time")
                || phrase.contains("firsttime") || phrase.equals("new")
                || phrase.contains("new application")) {
            return "FRESH";
        }
        return null;
    }

    /**
     * Canonicalizes a course/degree spelling ("btech" -> "B.Tech"). A value that
     * is not a recognized degree (for example "Computer Science") is returned
     * cleaned, unchanged, so a specific course name is never lost.
     */
    public String normalizeCourse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String canonical = matchCourse(phrase(raw));
        return canonical != null ? canonical : raw.trim();
    }

    /**
     * Scans a short reply for a course/degree mention and returns the canonical
     * form. Used to backfill COURSE when the LLM composer consumed the answer as
     * an education level only. Restricted to short messages so a longer sentence
     * that merely mentions a degree cannot be mistaken for a course answer.
     */
    public Optional<String> detectCourse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String phrase = phrase(text);
        if (phrase.split(" ").length > 4) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchCourse(phrase));
    }

    /**
     * Like {@link #detectCourse} but without the short-message guard. Used only
     * when the assistant has just asked for the course, so a longer answer such
     * as "I am doing btech" still resolves.
     */
    public Optional<String> detectCourseLoose(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchCourse(phrase(text)));
    }

    public Optional<String> detectCourseForProfile(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String phrase = phrase(text);
        if (!COURSE_CONTEXT.matcher(phrase).find()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchCourse(phrase));
    }

    public Optional<Integer> detectCurrentYear(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = CURRENT_YEAR.matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return Optional.ofNullable(YEAR_WORDS.get(value.toLowerCase(Locale.ROOT)));
        }
        matcher = CURRENT_YEAR_CONTEXT.matcher(text);
        if (matcher.find()) {
            return Optional.ofNullable(YEAR_WORDS.get(matcher.group(1).toLowerCase(Locale.ROOT)));
        }
        return Optional.empty();
    }

    public Optional<BigDecimal> detectAnnualFamilyIncome(String text) {
        if (text == null || text.isBlank() || RANGE_OR_APPROXIMATION.matcher(text).find()) {
            return Optional.empty();
        }
        Matcher matcher = INCOME.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String unit = matcher.group(2);
        long multiplier = 1L;
        if (unit != null) {
            multiplier = switch (unit.toLowerCase(Locale.ROOT)) {
                case "lakh", "lakhs", "lac", "lacs" -> 100_000L;
                case "crore", "crores", "cr" -> 10_000_000L;
                case "k", "thousand" -> 1_000L;
                default -> 1L;
            };
        } else if (amount.compareTo(BigDecimal.valueOf(1_000)) < 0) {
            return Optional.empty();
        }
        return Optional.of(amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    public Optional<String> detectDomicileState(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String phrase = phrase(text);
        for (Map.Entry<String, String> entry : STATES.entrySet()) {
            String state = entry.getKey();
            if (phrase.contains("i am from " + state)
                    || phrase.contains("i'm from " + state)
                    || phrase.contains("student from " + state)
                    || phrase.contains("resident of " + state)
                    || phrase.contains("domiciled in " + state)
                    || phrase.contains("domicile is " + state)
                    || phrase.contains("domicile in " + state)
                    || phrase.contains("state is " + state)
                    || phrase.contains("state of domicile is " + state)
                    || phrase.contains("belong to " + state)
                    || phrase.contains("hail from " + state)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public Optional<Boolean> detectRegularMode(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String phrase = phrase(text);
        if (phrase.contains("not regular") || phrase.contains("non regular")
                || phrase.contains("part time") || phrase.contains("distance")
                || phrase.contains("correspondence") || phrase.contains("open university")) {
            return Optional.of(Boolean.FALSE);
        }
        if (phrase.contains("regular") || phrase.contains("full time")) {
            return Optional.of(Boolean.TRUE);
        }
        return Optional.empty();
    }

    public Optional<Boolean> detectPriorDegree(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String phrase = phrase(text);
        if (NEGATED_PRIOR_DEGREE.matcher(phrase).find()) {
            return Optional.of(Boolean.FALSE);
        }
        if (PRIOR_DEGREE.matcher(phrase).find()) {
            return Optional.of(Boolean.TRUE);
        }
        return Optional.empty();
    }

    public Optional<String> detectNationality(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String phrase = phrase(text);
        if (phrase.contains("indian citizen") || phrase.contains("indian national")
                || phrase.contains("i am indian") || phrase.contains("i am an indian")
                || phrase.contains("nationality is indian") || phrase.contains("nationality indian")) {
            return Optional.of("Indian");
        }
        return Optional.empty();
    }

    private String matchCourse(String phrase) {
        for (Map.Entry<String, String> entry : COURSE_MAP.entrySet()) {
            if (entry.getKey().indexOf(' ') >= 0 && phrase.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (String token : phrase.split(" ")) {
            String canonical = COURSE_MAP.get(token);
            if (canonical != null) {
                return canonical;
            }
        }
        return null;
    }

    private String matchSocialCategory(String phrase, boolean conservative) {
        if (phrase.contains("denotified") || phrase.contains("nomadic")
                || phrase.contains("vimukta") || phrase.contains("vjnt")) {
            return "DNT";
        }
        if (phrase.contains("economically backward") || phrase.contains("economical backward")
                || phrase.contains("educationally backward")) {
            return "EBC";
        }
        if (phrase.contains("economically weaker") || phrase.contains("economical weaker")) {
            return "EWS";
        }
        if (phrase.contains("other backward") || phrase.contains("backward class")
                || phrase.contains("backward caste")) {
            return "OBC";
        }
        if (!conservative && phrase.contains("backward")) {
            return "OBC";
        }
        if (phrase.contains("scheduled caste") || phrase.contains("schedule caste")) {
            return "SC";
        }
        if (phrase.contains("scheduled tribe") || phrase.contains("schedule tribe")) {
            return "ST";
        }
        if (phrase.contains("minorit")) {
            return "MINORITY";
        }
        if (phrase.contains("unreserved") || phrase.contains("general category")
                || phrase.contains("general caste")) {
            return "GENERAL";
        }
        if (!conservative && phrase.contains("general")) {
            return "GENERAL";
        }

        for (String token : phrase.split(" ")) {
            if (conservative) {
                if (DETECT_TOKENS.contains(token)) {
                    return switch (token) {
                        case "obc" -> "OBC";
                        case "ebc" -> "EBC";
                        case "ews" -> "EWS";
                        case "dnt" -> "DNT";
                        case "sc" -> "SC";
                        default -> "ST";
                    };
                }
                continue;
            }
            if (GENERAL_TOKENS.contains(token)) {
                return "GENERAL";
            }
            if (OBC_TOKENS.contains(token)) {
                return "OBC";
            }
            if (EBC_TOKENS.contains(token)) {
                return "EBC";
            }
            if (EWS_TOKENS.contains(token)) {
                return "EWS";
            }
            if (DNT_TOKENS.contains(token)) {
                return "DNT";
            }
            if (SC_TOKENS.contains(token)) {
                return "SC";
            }
            if (ST_TOKENS.contains(token)) {
                return "ST";
            }
        }
        return null;
    }

    private String matchEducationLevel(String phrase) {
        // Postgraduate first: "postgraduate"/"post graduate" also contains the
        // substring "graduate" used below for UG.
        if (phrase.contains("postgraduate") || phrase.contains("post graduate")
                || phrase.contains("pg") || phrase.contains("master")
                || phrase.contains("m.tech") || phrase.contains("mtech")
                || phrase.contains("m tech")
                || phrase.contains("msc") || phrase.contains("mcom")
                || phrase.contains("phd") || phrase.contains("doctorate")) {
            return "PG";
        }
        if (phrase.contains("undergraduate") || phrase.contains("under graduate")
                || phrase.contains("ug") || phrase.contains("graduation")
                || phrase.contains("graduate") || phrase.contains("bachelor")
                || phrase.contains("b.tech") || phrase.contains("btech")
                || phrase.contains("b tech")
                || phrase.contains("bsc") || phrase.contains("bcom")
                || phrase.contains("bca") || phrase.contains("bba")
                || phrase.contains("degree") || phrase.contains("college")
                || phrase.contains("university") || phrase.contains("diploma")
                || phrase.contains("polytechnic")) {
            return "UG";
        }
        if (phrase.contains("hsc") || phrase.contains("intermediate")
                || phrase.contains("plus two") || phrase.contains("plus2")
                || phrase.contains("senior secondary") || phrase.contains("higher secondary")
                || phrase.contains("puc") || phrase.contains("12th")
                || phrase.contains("twelfth") || phrase.contains("xii")
                || phrase.contains("class 12") || phrase.contains("12 standard")) {
            return "CLASS_12";
        }
        if (phrase.contains("plus one") || phrase.contains("plus1")
                || phrase.contains("11th") || phrase.contains("eleventh")
                || phrase.contains("xi") || phrase.contains("class 11")
                || phrase.contains("11 standard")) {
            return "CLASS_11";
        }
        if (phrase.contains("matric") || phrase.contains("ssc")
                || phrase.contains("10th") || phrase.contains("tenth")
                || phrase.contains("class 10") || phrase.contains("10 standard")) {
            return "CLASS_10";
        }
        if (phrase.contains("9th") || phrase.contains("ninth")
                || phrase.contains("class 9") || phrase.contains("9 standard")) {
            return "CLASS_9";
        }
        return null;
    }

    /**
     * Lowercase, punctuation to single spaces, collapsed. "OBC-NCL" and
     * "obc ncl" both become "obc ncl".
     */
    private String phrase(String raw) {
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll(" +", " ");
    }
}
