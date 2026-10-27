package com.bharat.scholarship_rag_backend.enrichment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class QueryGate {

    private static final List<Pattern> REFERENCE_PATTERNS = List.of(
            Pattern.compile("\\b(it|this|that|these|those|there)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(is|are|does|do|can|will|should)\\s+(that|he|she|they)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(so|lately|how about)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwhat about\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwhat then\\b|\\bwhy then\\b|\\bwhy\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final int MAX_CLEAN_LENGTH = 512;

    public boolean needsContext(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        for (Pattern pattern : REFERENCE_PATTERNS) {
            if (pattern.matcher(query).find()) {
                return true;
            }
        }
        return clean(query).split("\\s+").length < 3;
    }

    public String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[?!]+$", "")
                .replaceAll("(?<![0-9])\\.+$", "")
                .replaceAll("[\\p{Punct}&&[^'\\-+.,%]]", "");
        if (cleaned.length() > MAX_CLEAN_LENGTH) {
            cleaned = cleaned.substring(0, MAX_CLEAN_LENGTH);
        }
        return cleaned;
    }
}