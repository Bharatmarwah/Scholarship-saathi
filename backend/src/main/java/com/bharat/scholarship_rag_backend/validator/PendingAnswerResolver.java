package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a student's plain reply onto the field(s) the assistant just asked
 * about. This is the deterministic half of the hybrid: even when the LLM
 * composer fails to extract the answer, the reply is understood because the
 * question it answers is known.
 *
 * <p>It is deliberately conservative. It only runs when the previous assistant
 * turn asked a specific field, and it refuses to treat a new question or a new
 * scheme request as an answer. Values it produces still pass through the
 * profile service's own type and plausibility guards.
 */
@Component
public class PendingAnswerResolver {

    private static final Pattern FIRST_INT = Pattern.compile("(\\d+)");
    private static final Pattern FIRST_DOUBLE = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern INCOME = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(lakh|lakhs|lac|lacs|crore|crores|cr|k|thousand|l)?");

    private static final Pattern RANGE_OR_APPROXIMATION = Pattern.compile(
            "\\b(below|less than|under|above|more than|over|around|about|approx\\w*|"
                    + "between|upto|up to|nearly|roughly)\\b|\\d\\s*(to|-|–)\\s*\\d");

    private static final Set<String> YES = Set.of(
            "yes", "yeah", "yep", "yup", "sure", "true", "correct", "right",
            "haan", "han", "ha", "definitely", "obviously", "ofcourse",
            "do", "have", "does");

    /**
     * Negations are checked before affirmations and always win, so a reply
     * such as "I don't have a disability" is not read as YES just because it
     * contains "have". Contractions are tokenized without the apostrophe
     * ("don't" -> "dont"), which is why both spellings appear here.
     */
    private static final Set<String> NO = Set.of(
            "no", "nope", "false", "none", "never", "nahi", "nah", "not",
            "dont", "doesnt", "didnt", "wont", "cant", "cannot", "without",
            "havent", "hasnt", "hadnt", "isnt", "arent", "wasnt", "werent");

    /** Tokens that mark the message as a fresh question or request, not an answer. */
    private static final Set<String> REQUEST_TOKENS = Set.of(
            "what", "why", "how", "which", "when", "who", "whose", "where",
            "eligible", "eligibility", "apply", "application", "deadline",
            "list", "tell", "could", "would", "should", "please", "compare",
            "difference", "explain");

    private final ProfileValueNormalizer normalizer;

    public PendingAnswerResolver(ProfileValueNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    /**
     * @param askedFields      fields the assistant asked for in the previous turn
     * @param userText         the student's raw reply
     * @param alreadyExtracted field names the LLM already produced this turn
     * @return field name to value for every asked field still missing, empty when
     *         the reply is not answer-like
     */
    public Map<String, Object> resolve(
            List<StudentProfileField> askedFields,
            String userText,
            Set<String> alreadyExtracted) {

        if (askedFields == null || askedFields.isEmpty() || userText == null || userText.isBlank()) {
            return Map.of();
        }
        String text = userText.trim();
        if (!looksLikeAnswer(text)) {
            return Map.of();
        }

        boolean single = askedFields.size() == 1;
        List<String> tokens = tokens(text);
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (StudentProfileField field : askedFields) {
            String name = field.name();
            if (alreadyExtracted != null && alreadyExtracted.contains(name)) {
                continue;
            }
            resolveField(field, text, tokens, single).ifPresent(value -> resolved.putIfAbsent(name, value));
        }
        return resolved;
    }

    private Optional<Object> resolveField(
            StudentProfileField field,
            String text,
            List<String> tokens,
            boolean single) {

        return switch (field) {
            case EDUCATION_LEVEL -> normalizer.detectEducationLevel(text).map(value -> (Object) value);

            case APPLICATION_TYPE -> normalizer.detectApplicationType(text).map(value -> (Object) value);

            case SOCIAL_CATEGORY -> normalizer.detectSocialCategory(text)
                    .<Object>map(value -> value)
                    .or(() -> single && tokens.size() <= 3
                            ? Optional.of((Object) text)
                            : Optional.empty());

            case COURSE -> normalizer.detectCourseLoose(text)
                    .<Object>map(value -> value)
                    .or(() -> single && tokens.size() <= 5
                            ? Optional.of((Object) text)
                            : Optional.empty());

            case CURRENT_YEAR, CURRENT_CLASS, DISABILITY_PERCENTAGE ->
                    single ? firstInt(text).map(value -> (Object) value) : Optional.empty();

            case CLASS12_PERCENTILE, PREVIOUS_CLASS_MARKS ->
                    single ? firstDouble(text).map(value -> (Object) value) : Optional.empty();

            case ANNUAL_FAMILY_INCOME ->
                    single ? parseIncome(text).map(value -> (Object) value) : Optional.empty();

            case REGULAR_MODE, HAS_PRIOR_DEGREE, RECEIVING_OTHER_SCHOLARSHIP, HAS_DISABILITY ->
                    single ? yesNo(tokens).map(value -> (Object) value) : Optional.empty();

            case BOARD, DOMICILE_STATE, INSTITUTION_NAME, INSTITUTION_TYPE, NATIONALITY ->
                    single && tokens.size() <= 6 ? Optional.of(text) : Optional.empty();

            default -> Optional.empty();
        };
    }

    /**
     * A reply is treated as an answer only when it is a short statement that is
     * not itself a question and does not read like a new scheme request.
     */
    private boolean looksLikeAnswer(String text) {
        if (text.endsWith("?")) {
            return false;
        }
        List<String> tokens = tokens(text);
        if (tokens.isEmpty() || tokens.size() > 8) {
            return false;
        }
        return tokens.stream().noneMatch(REQUEST_TOKENS::contains);
    }

    private Optional<Integer> firstInt(String text) {
        Matcher matcher = FIRST_INT.matcher(text.replace(",", ""));
        return matcher.find() ? Optional.of(Integer.valueOf(matcher.group(1))) : Optional.empty();
    }

    private Optional<Double> firstDouble(String text) {
        Matcher matcher = FIRST_DOUBLE.matcher(text.replace(",", ""));
        return matcher.find() ? Optional.of(Double.valueOf(matcher.group(1))) : Optional.empty();
    }

    private Optional<BigDecimal> parseIncome(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String compact = lower.replace(",", "");
        List<String> tokens = tokens(lower);
        if (tokens.stream().anyMatch(token -> token.equals("nil") || token.equals("none"))
                || compact.contains("no income") || compact.contains("zero income")) {
            return Optional.of(BigDecimal.ZERO);
        }
        // A range or approximation is not an exact annual income. Storing its
        // boundary as the value could change an eligibility verdict, so omit it
        // and let the normal follow-up question ask again.
        if (RANGE_OR_APPROXIMATION.matcher(lower).find()) {
            return Optional.empty();
        }
        Matcher matcher = INCOME.matcher(compact);
        if (!matcher.find()) {
            return Optional.empty();
        }
        double base = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        long multiplier = 1;
        if (unit != null) {
            multiplier = switch (unit) {
                case "lakh", "lakhs", "lac", "lacs", "l" -> 100_000L;
                case "crore", "crores", "cr" -> 10_000_000L;
                case "k", "thousand" -> 1_000L;
                default -> 1L;
            };
        }
        return Optional.of(BigDecimal.valueOf(base * multiplier));
    }

    private Optional<Boolean> yesNo(List<String> tokens) {
        // Negation wins regardless of token order. Looking for YES first made
        // "I don't have a disability" resolve to true because it contains
        // "have".
        for (String token : tokens) {
            if (NO.contains(token)) {
                return Optional.of(Boolean.FALSE);
            }
        }
        for (String token : tokens) {
            if (YES.contains(token)) {
                return Optional.of(Boolean.TRUE);
            }
        }
        return Optional.empty();
    }

    private List<String> tokens(String text) {
        // Apostrophes are removed before punctuation is replaced with spaces so
        // contractions stay one token ("don't" -> "dont", not "don" + "t").
        // The split "don t" never matched the NO set and let "have" win.
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replaceAll("['\u2019\u2018`]", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return List.of(cleaned.split(" +"));
    }
}
