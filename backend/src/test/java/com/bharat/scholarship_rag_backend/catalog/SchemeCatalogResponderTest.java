package com.bharat.scholarship_rag_backend.catalog;

import com.bharat.scholarship_rag_backend.eligibility.SchemeEligibilityCatalog;
import com.bharat.scholarship_rag_backend.scheme.SchemeMetadataCatalog;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemeCatalogResponderTest {

    private final SchemeCatalogResponder responder = new SchemeCatalogResponder(
            new SchemeRegistry(),
            new SchemeMetadataCatalog(),
            new SchemeEligibilityCatalog());

    @Test
    void recognisesQuestionsAboutTheCatalogue() {
        assertTrue(responder.isCatalogQuery("which schemes do you have?"));
        assertTrue(responder.isCatalogQuery("how many scholarship schemes do you have?"));
        assertTrue(responder.isCatalogQuery("what schemes are available?"));
        assertTrue(responder.isCatalogQuery("list all scholarships"));
        assertTrue(responder.isCatalogQuery("show me the schemes you support"));
        assertTrue(responder.isCatalogQuery("are there any scholarships?"));
        assertTrue(responder.isCatalogQuery("tell me which schemes you have"));
        assertTrue(responder.isCatalogQuery("do you have any schemes?"));
    }

    @Test
    void leavesRuleAndEligibilityQuestionsToTheNormalFlows() {
        assertFalse(responder.isCatalogQuery(
                "What is the family income limit for Top Class SC?"));
        assertFalse(responder.isCatalogQuery(
                "What documents are required for Ishan Uday?"));
        assertFalse(responder.isCatalogQuery(
                "which scholarships am I eligible for?"));
        assertFalse(responder.isCatalogQuery(
                "what schemes are available for SC students?"));
        assertFalse(responder.isCatalogQuery(
                "which scheme is best for me?"));
        assertFalse(responder.isCatalogQuery(
                "how many marks do I need for Top Class SC?"));
        assertFalse(responder.isCatalogQuery("what is a scholarship?"));
        assertFalse(responder.isCatalogQuery("tell me about PM-USP CSSS"));
    }

    @Test
    void leavesTargetGroupQuestionsToEligibilityOrRetrieval() {
        assertFalse(responder.isCatalogQuery(
                "which schemes are available to SC students?"));
        assertFalse(responder.isCatalogQuery(
                "which scholarships are available to women?"));
        assertFalse(responder.isCatalogQuery(
                "which schemes are available to students with disabilities?"));
        assertFalse(responder.isCatalogQuery(
                "which schemes are available to postgraduate students?"));
        assertFalse(responder.isCatalogQuery(
                "which scholarships are available to 12th class students?"));
        assertFalse(responder.isCatalogQuery(
                "which schemes are available to students from the North-East?"));
    }

    @Test
    void recognisesGenericQuestionsAboutWhereGuidelinesLive() {
        assertTrue(responder.isGuidelinesLocationQuery(
                "Where are Scheme Guidelines available?"));
        assertTrue(responder.isGuidelinesLocationQuery(
                "where can I find the scheme guidelines?"));
        assertTrue(responder.isGuidelinesLocationQuery(
                "where can i download the guidelines"));
    }

    @Test
    void leavesSchemeSpecificAndRuleQuestionsToRetrieval() {
        assertFalse(responder.isGuidelinesLocationQuery(
                "Where are the Top Class SC guidelines available?"));
        assertFalse(responder.isGuidelinesLocationQuery(
                "Where are the guidelines available for PM-USP CSSS?"));
        assertFalse(responder.isGuidelinesLocationQuery(
                "Where are the income guidelines available?"));
        assertFalse(responder.isGuidelinesLocationQuery(
                "Where can I apply for the scholarship?"));
    }

    @Test
    void guidelinesLocationAnswerNamesThePortalAndInvitesAScheme() {
        String answer = responder.guidelinesLocationAnswer();

        assertTrue(answer.contains("administering department or ministry"), answer);
        assertTrue(answer.contains("National Scholarship Portal"), answer);
        assertTrue(answer.contains("scholarships.gov.in"), answer);
        assertTrue(answer.contains("which scheme"), answer);
    }

    @Test
    void answersWithEveryConfiguredSchemeAndTheCount() {
        String answer = responder.answer();

        assertTrue(answer.contains("5 Central Government scholarship schemes"),
                "the answer must state how many schemes are supported: " + answer);
        assertTrue(answer.contains("PM-USP CSSS"), answer);
        assertTrue(answer.contains("Ishan Uday"), answer);
        assertTrue(answer.contains("PM YASASVI Top Class School"), answer);
        assertTrue(answer.contains("Top Class Education - SC"), answer);
        assertTrue(answer.contains("Top Class Education - PwD"), answer);

        assertTrue(answer.contains("1. **"), answer);
        assertTrue(answer.contains("5. **"), answer);
    }

    @Test
    void descriptionsComeFromTheEligibilityCriteria() {
        String answer = responder.answer();

        assertTrue(answer.contains("Rs 4.5 lakh"), answer);
        assertTrue(answer.contains("Rs 2.5 lakh"), answer);
        assertTrue(answer.contains("Rs 8 lakh"), answer);
        assertTrue(answer.contains("North-East"), answer);
        assertTrue(answer.contains("OBC/EBC/DNT"), answer);
        assertTrue(answer.contains("40% or more"), answer);
        assertTrue(answer.contains("classes 9–12"), answer);
    }
}
