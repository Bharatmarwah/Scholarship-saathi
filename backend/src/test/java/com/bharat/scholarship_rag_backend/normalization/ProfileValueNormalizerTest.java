package com.bharat.scholarship_rag_backend.normalization;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileValueNormalizerTest {

    private final ProfileValueNormalizer normalizer = new ProfileValueNormalizer();

    @Test
    void normalizesEveryCommonCategoryPhrasing() {
        assertEquals("GENERAL", normalizer.normalizeSocialCategory("General"));
        assertEquals("GENERAL", normalizer.normalizeSocialCategory("UR"));
        assertEquals("GENERAL", normalizer.normalizeSocialCategory("unreserved"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("OBC"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("OBC-NCL"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("Other Backward Class"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("backward caste"));
        assertEquals("EBC", normalizer.normalizeSocialCategory("EBC"));
        assertEquals("EBC", normalizer.normalizeSocialCategory("economically backward class"));
        assertEquals("DNT", normalizer.normalizeSocialCategory("DNT"));
        assertEquals("DNT", normalizer.normalizeSocialCategory("denotified and nomadic tribe"));
        assertEquals("SC", normalizer.normalizeSocialCategory("Scheduled Caste"));
        assertEquals("SC", normalizer.normalizeSocialCategory("sc"));
        assertEquals("ST", normalizer.normalizeSocialCategory("scheduled tribe"));
        assertEquals("EWS", normalizer.normalizeSocialCategory("economically weaker section"));
        assertEquals("MINORITY", normalizer.normalizeSocialCategory("minority"));
    }

    @Test
    void keepsUnrecognizedCategoryInsteadOfDroppingIt() {
        assertEquals("creamy layer", normalizer.normalizeSocialCategory("creamy layer"));
    }

    @Test
    void detectsCategoryInFreeTextAndIgnoresUnrelatedText() {
        assertEquals("OBC", normalizer.detectSocialCategory("I belong to OBC category").orElseThrow());
        assertEquals("EBC", normalizer.detectSocialCategory("we are an EBC family").orElseThrow());
        assertEquals("SC", normalizer.detectSocialCategory("I am a scheduled caste student").orElseThrow());
        assertTrue(normalizer.detectSocialCategory("I want to apply for a scholarship").isEmpty());
        assertTrue(normalizer.detectSocialCategory("what is ur income limit").isEmpty());
        assertTrue(normalizer.detectSocialCategory("give me general information").isEmpty());
    }

    @Test
    void normalizesEducationLevelToRoutingTokens() {
        assertEquals("CLASS_12", normalizer.normalizeEducationLevel("Class 12"));
        assertEquals("CLASS_12", normalizer.normalizeEducationLevel("12th"));
        assertEquals("CLASS_12", normalizer.normalizeEducationLevel("intermediate"));
        assertEquals("CLASS_11", normalizer.normalizeEducationLevel("11th standard"));
        assertEquals("CLASS_10", normalizer.normalizeEducationLevel("matric"));
        assertEquals("UG", normalizer.normalizeEducationLevel("B.Tech"));
        assertEquals("UG", normalizer.normalizeEducationLevel("undergraduate"));
        assertEquals("UG", normalizer.normalizeEducationLevel("college"));
        assertEquals("PG", normalizer.normalizeEducationLevel("postgraduate"));
        assertEquals("PG", normalizer.normalizeEducationLevel("Masters"));
    }

    @Test
    void normalizesFreshAndRenewalApplicationTypes() {
        assertEquals("FRESH", normalizer.normalizeApplicationType("fresh"));
        assertEquals("FRESH", normalizer.normalizeApplicationType("first time applicant"));
        assertEquals("RENEWAL", normalizer.normalizeApplicationType("renewal"));
        assertEquals("RENEWAL", normalizer.normalizeApplicationType("I want to renew"));
        assertEquals("FRESH", normalizer.normalizeApplicationType("new"));
        assertEquals("FRESH", normalizer.detectApplicationType("fresh application").orElseThrow());
        assertEquals("RENEWAL", normalizer.detectApplicationType("renewal").orElseThrow());
        assertTrue(normalizer.detectApplicationType("second year").isEmpty());
    }

    @Test
    void normalizesCourseSpellingsToCanonicalForm() {
        assertEquals("B.Tech", normalizer.normalizeCourse("btech"));
        assertEquals("B.Tech", normalizer.normalizeCourse("B.Tech"));
        assertEquals("B.Tech", normalizer.normalizeCourse("B Tech"));
        assertEquals("M.Tech", normalizer.normalizeCourse("m.tech"));
        assertEquals("BSc", normalizer.normalizeCourse("bsc"));
        assertEquals("BCA", normalizer.normalizeCourse("BCA"));
        assertEquals("MBA", normalizer.normalizeCourse("mba"));
        assertEquals("MBBS", normalizer.normalizeCourse("mbbs"));
        assertEquals("B.Com", normalizer.normalizeCourse("bcom"));
        assertEquals("Computer Science", normalizer.normalizeCourse("Computer Science"));
    }

    @Test
    void detectsCourseFromShortReplyButNotFromLongSentences() {
        assertEquals("B.Tech", normalizer.detectCourse("btech").orElseThrow());
        assertEquals("B.Tech", normalizer.detectCourse("I am doing btech").orElseThrow());
        assertEquals("MBA", normalizer.detectCourse("mba").orElseThrow());
        assertTrue(normalizer.detectCourse("I want to apply for a scholarship").isEmpty());
        assertTrue(normalizer.detectCourse("what is your income limit").isEmpty());
        assertTrue(normalizer.detectCourse(
                "can you tell me which scholarships I am eligible for right now").isEmpty());
    }

    @Test
    void extractsProfileFactsFromAFullFirstPersonMessage() {
        String query = "I am an Indian student from Assam, currently in the first year of a "
                + "regular/full-time B.Sc undergraduate course. My annual family income is "
                + "\u20b93,00,000. I have not completed any undergraduate degree before and "
                + "this is a fresh application.";

        assertEquals(1, normalizer.detectCurrentYear(query).orElseThrow());
        assertEquals(0, new BigDecimal("300000")
                .compareTo(normalizer.detectAnnualFamilyIncome(query).orElseThrow()));
        assertEquals("Assam", normalizer.detectDomicileState(query).orElseThrow());
        assertEquals("BSc", normalizer.detectCourseForProfile(query).orElseThrow());
        assertEquals(Boolean.TRUE, normalizer.detectRegularMode(query).orElseThrow());
        assertEquals(Boolean.FALSE, normalizer.detectPriorDegree(query).orElseThrow());
        assertEquals("Indian", normalizer.detectNationality(query).orElseThrow());
    }

    @Test
    void profileFactDetectorsStayConservative() {
        assertTrue(normalizer.detectCurrentYear("what is the income limit").isEmpty());
        assertTrue(normalizer.detectAnnualFamilyIncome("what is the income limit?").isEmpty());
        assertTrue(normalizer.detectAnnualFamilyIncome("my family income is below 3 lakh").isEmpty());
        assertTrue(normalizer.detectDomicileState("scholarships for students from Assam").isEmpty());
        assertTrue(normalizer.detectCourseForProfile("what about msc?").isEmpty());
        assertTrue(normalizer.detectPriorDegree("what is the income limit").isEmpty());
    }
}
