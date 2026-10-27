package com.bharat.scholarship_rag_backend.eligibility;

import java.util.Locale;
import java.util.Optional;

/**
 * Coarse education stage a scheme targets. Derived from the free-text
 * {@code educationLevel} on the profile using the same vocabulary the
 * validation engine already understands, so a value that keeps discovery
 * running also keeps eligibility evaluation running.
 */
public enum EducationStage {
    SCHOOL,
    UNDERGRADUATE,
    POSTGRADUATE;

    public static Optional<EducationStage> detect(String educationLevel) {
        if (educationLevel == null || educationLevel.isBlank()) {
            return Optional.empty();
        }
        String level = educationLevel.toLowerCase(Locale.ROOT);
        if (isSchool(level)) {
            return Optional.of(SCHOOL);
        }
        if (isPostgraduate(level)) {
            return Optional.of(POSTGRADUATE);
        }
        if (isUndergraduate(level)) {
            return Optional.of(UNDERGRADUATE);
        }
        return Optional.empty();
    }

    private static boolean isSchool(String level) {
        return level.contains("class")
                || level.contains("9th")
                || level.contains("10th")
                || level.contains("11th")
                || level.contains("12th")
                || level.contains("secondary")
                || level.contains("school")
                || level.contains("ssc")
                || level.contains("hsc")
                || level.contains("matric")
                || level.contains("std")
                || level.contains("standard")
                || level.contains("intermediate")
                || level.contains("plus two")
                || level.contains("plus2")
                || level.contains("puc")
                || level.contains("+2")
                || level.contains("+1");
    }

    private static boolean isPostgraduate(String level) {
        return level.contains("pg")
                || level.contains("postgraduate")
                || level.contains("post graduate")
                || level.contains("master")
                || level.contains("m.tech")
                || level.contains("mtech")
                || level.contains("msc")
                || level.contains("mcom")
                || level.contains("mba")
                || level.contains("phd")
                || level.contains("doctorate")
                || level.startsWith("m.");
    }

    private static boolean isUndergraduate(String level) {
        return level.contains("ug")
                || level.contains("undergraduate")
                || level.contains("under graduate")
                || level.contains("graduate")
                || level.contains("bachelor")
                || level.contains("b.tech")
                || level.contains("btech")
                || level.contains("bba")
                || level.contains("bca")
                || level.contains("bsc")
                || level.contains("bcom")
                || level.contains("degree")
                || level.contains("diploma")
                || level.contains("polytechnic")
                || level.contains("college")
                || level.contains("university")
                || level.startsWith("b.");
    }
}
