package com.bharat.scholarship_rag_backend.catalog;

import com.bharat.scholarship_rag_backend.eligibility.EducationStage;
import com.bharat.scholarship_rag_backend.eligibility.EligibilityCriteria;
import com.bharat.scholarship_rag_backend.eligibility.SchemeEligibilityCatalog;
import com.bharat.scholarship_rag_backend.scheme.SchemeMetadataCatalog;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Answers meta-questions about the assistant's own catalogue ("which schemes
 * do you have?", "how many scholarships are there?") without retrieval.
 *
 * <p>These questions are about the application, not about a fact written in a
 * scheme guideline. Sending them through the vector store is wrong twice over:
 * a guideline does not contain a list of the schemes the application supports,
 * and an empty or sparse knowledge base turns the answer into a false "not
 * found". The catalogue is already encoded deterministically in
 * {@link SchemeRegistry}, so it is answered from there.
 *
 * <p>The one-line descriptions are derived from {@link SchemeEligibilityCatalog}
 * rather than written by hand, so the summary can never drift from the
 * eligibility rules the rest of the system enforces.
 *
 * <p>It also answers the generic "where are the scheme guidelines available?"
 * question. That question names no scheme, so retrieval has to pick between
 * every scheme's document; in practice it can return one scheme's portal page
 * and look arbitrary. The location is a stable meta-fact, so it is answered
 * deterministically here. A question that names a scheme is deliberately left
 * to retrieval, where the answer can be cited from that scheme's document.
 */
@Component
public class SchemeCatalogResponder {

    /**
     * Words that mark a question as being about a rule, a specific scheme, or
     * the student's own situation rather than about the catalogue itself. A
     * question containing any of these must go through the normal routing:
     * "which schemes are available for SC students?" is an eligibility
     * question, not a request to list everything.
     */
    private static final Pattern NOT_CATALOG = Pattern.compile(
            "\\b(eligible|eligibility|qualify|qualification|suit|suitable|best|recommend"
                    + "|apply|application|deadline|last date|document|benefit|amount|income"
                    + "|marks|percentage|percentile|cutoff|category|domicile|disability"
                    + "|renew|renewal|status|selection|compare|difference|criteria"
                    + "|requirement|tuition|fee|fees|cover|covers|coverage"
                    + "|for|my|am i|i am|can i|do i|should i)\\b"
                    + "|\\b(sc|st|obc|ebc|dnt|pwd)\\b"
                    + "|\\b(girl|girls|boys|boy|women|woman|female|male"
                    + "|minority|minorities|disabilit\\w*|disabled)\\b"
                    + "|\\b(class|classes|9th|10th|11th|12th|undergraduate"
                    + "|postgraduate|college|school|university)\\b"
                    + "|\\b(north east|north eastern|northeast)\\b"
                    + "|\\btop class\\b|\\bpm usp\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> CATALOG_QUESTIONS = List.of(
            Pattern.compile("\\b(which|what)\\s+(scholarship\\s+)?schemes?\\s+"
                    + "(do|does|are|is|you|have|exist|available|there|supported)\\b"),
            Pattern.compile("\\b(which|what)\\s+scholarships?\\s+"
                    + "(do|does|are|is|you|have|exist|available|there|supported)\\b"),
            Pattern.compile("\\b(list|show|display|name|enumerate)\\b.*\\b(schemes?|scholarships?)\\b"),
            Pattern.compile("\\bhow\\s+many\\b.*\\b(schemes?|scholarships?)\\b"),
            Pattern.compile("\\b(schemes?|scholarships?)\\s+"
                    + "(do\\s+you|you\\s+(have|support|cover|offer)|available\\s+here)\\b"),
            Pattern.compile("\\ball\\s+(the\\s+)?(available\\s+)?(schemes?|scholarships?)\\b"),
            Pattern.compile("\\bare\\s+there\\s+(any\\s+)?(scholarship\\s+)?schemes?\\b"),
            Pattern.compile("\\bare\\s+there\\s+(any\\s+)?scholarships?\\b"),
            Pattern.compile("\\bdo\\s+you\\s+(have|support|cover|offer)\\s+"
                    + "(any\\s+)?(scholarship\\s+)?schemes?\\b"),
            Pattern.compile("\\bdo\\s+you\\s+(have|support|cover|offer)\\s+(any\\s+)?scholarships?\\b"));

    private static final List<Pattern> GUIDELINE_LOCATION_QUESTIONS = List.of(
            Pattern.compile("\\bwhere\\b.*\\bguidelines?\\b.*"
                    + "\\b(available|find|download|published|located|access|get|see|view)\\b"),
            Pattern.compile("\\bwhere\\b.*"
                    + "\\b(available|find|download|published|located|access|get|see|view)\\b.*\\bguidelines?\\b"),
            Pattern.compile("\\bhow\\s+(can|do|should)\\s+i\\s+"
                    + "(find|get|access|download|see|view)\\b.*\\bguidelines?\\b"),
            Pattern.compile("\\bguidelines?\\b.*\\b(available|download|published|located)\\b"));

    /**
     * Rule-topic words that mean the question is about what the guidelines say,
     * not where they live. They must stay on the retrieval path.
     */
    private static final Pattern NOT_GUIDELINE_LOCATION = Pattern.compile(
            "\\b(eligib\\w*|income|qualif\\w*|apply|appl\\w*|deadline|last date|document\\w*"
                    + "|benefit\\w*|amount|criteria|requirement\\w*|renew\\w*|selection"
                    + "|status|marks|percentage|percentile|cutoff|rule\\w*|coverage)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String GUIDELINE_LOCATION_ANSWER =
            "Scheme guidelines are published by the administering department or ministry. "
                    + "Applications and updates are also available on the National Scholarship "
                    + "Portal (NSP): http://scholarships.gov.in/.\n\n"
                    + "If you tell me which scheme you mean, I can summarise its guidelines "
                    + "and cite the source document.";

    private static final List<String> CATEGORY_ORDER =
            List.of("SC", "ST", "OBC", "EBC", "DNT");

    private final SchemeRegistry schemeRegistry;
    private final SchemeMetadataCatalog metadataCatalog;
    private final SchemeEligibilityCatalog eligibilityCatalog;

    public SchemeCatalogResponder(
            SchemeRegistry schemeRegistry,
            SchemeMetadataCatalog metadataCatalog,
            SchemeEligibilityCatalog eligibilityCatalog) {
        this.schemeRegistry = schemeRegistry;
        this.metadataCatalog = metadataCatalog;
        this.eligibilityCatalog = eligibilityCatalog;
    }

    /**
     * True when the query asks what schemes exist in the assistant's catalogue.
     * Deliberately conservative: questions that mention eligibility, documents,
     * income, a scheme name, or the student's own circumstances are left to the
     * normal retrieval and eligibility flows.
     */
    public boolean isCatalogQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = normalize(query);
        if (normalized.isEmpty() || NOT_CATALOG.matcher(normalized).find()) {
            return false;
        }
        return CATALOG_QUESTIONS.stream()
                .anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    /**
     * True when the query asks where scheme guidelines can be found, without
     * naming a scheme or asking about a specific rule. A named scheme is left
     * to retrieval so the response can cite that scheme's own document.
     */
    public boolean isGuidelinesLocationQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = normalize(query);
        if (normalized.isEmpty()
                || NOT_GUIDELINE_LOCATION.matcher(normalized).find()
                || !schemeRegistry.resolveAll(normalized).isEmpty()) {
            return false;
        }
        return GUIDELINE_LOCATION_QUESTIONS.stream()
                .anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    /**
     * Deterministic answer for {@link #isGuidelinesLocationQuery(String)}.
     */
    public String guidelinesLocationAnswer() {
        return GUIDELINE_LOCATION_ANSWER;
    }

    /**
     * The deterministic answer. One list entry per supported scheme, with the
     * identifying stage, target group and income limit derived from the same
     * criteria the eligibility engine uses.
     */
    public String answer() {
        List<SchemeType> schemes = schemeRegistry.allSchemes();
        StringBuilder answer = new StringBuilder();
        answer.append("I can help with ").append(schemes.size())
                .append(" Central Government scholarship schemes:\n\n");

        for (int i = 0; i < schemes.size(); i++) {
            SchemeType scheme = schemes.get(i);
            answer.append(i + 1).append(". **")
                    .append(metadataCatalog.forScheme(scheme).getSchemeName())
                    .append("** - ")
                    .append(describe(scheme))
                    .append('\n');
        }

        answer.append("\nAsk me about any of these for eligibility, benefits, documents, or deadlines.");
        return answer.toString();
    }

    private String describe(SchemeType scheme) {
        EligibilityCriteria criteria = eligibilityCatalog.criteria(scheme);
        List<String> parts = new ArrayList<>();
        parts.add(stageText(criteria));

        String group = groupText(criteria);
        if (!group.isBlank()) {
            parts.add(group);
        }

        String income = incomeText(criteria);
        if (!income.isBlank()) {
            parts.add(income);
        }

        return String.join("; ", parts) + ".";
    }

    private static String stageText(EligibilityCriteria criteria) {
        Set<EducationStage> stages = criteria.stages();
        if (stages.contains(EducationStage.SCHOOL)) {
            if (criteria.minClass() != null && criteria.maxClass() != null) {
                return "classes " + criteria.minClass() + "–" + criteria.maxClass();
            }
            return "school study";
        }
        boolean undergraduate = stages.contains(EducationStage.UNDERGRADUATE);
        boolean postgraduate = stages.contains(EducationStage.POSTGRADUATE);
        if (undergraduate && postgraduate) {
            return "undergraduate or postgraduate study";
        }
        if (undergraduate) {
            return "undergraduate study";
        }
        if (postgraduate) {
            return "postgraduate study";
        }
        return "study";
    }

    private static String groupText(EligibilityCriteria criteria) {
        List<String> groups = new ArrayList<>();

        if (!criteria.eligibleSocialCategories().isEmpty()) {
            groups.add("for " + orderedCategories(criteria.eligibleSocialCategories()) + " students");
        }
        if (!criteria.eligibleDomicileStates().isEmpty()) {
            groups.add("for students from the North-East");
        }
        if (criteria.disabilityRequired()) {
            String percentage = criteria.minDisabilityPercentage() == null
                    ? ""
                    : " of " + criteria.minDisabilityPercentage() + "% or more";
            groups.add("for students with a benchmark disability" + percentage);
        }

        return String.join(" and ", groups);
    }

    private static String orderedCategories(Set<String> categories) {
        List<String> ordered = new ArrayList<>();
        for (String category : CATEGORY_ORDER) {
            if (categories.contains(category)) {
                ordered.add(category);
            }
        }
        if (ordered.size() != categories.size()) {
            categories.stream()
                    .filter(category -> !ordered.contains(category))
                    .sorted()
                    .forEach(ordered::add);
        }
        return String.join("/", ordered);
    }

    private static String incomeText(EligibilityCriteria criteria) {
        BigDecimal ceiling = criteria.incomeCeiling();
        if (ceiling == null) {
            return "";
        }
        String amount = "Rs " + lakhs(ceiling) + " lakh";
        return criteria.incomeCeilingInclusive()
                ? "family income up to " + amount
                : "family income below " + amount;
    }

    private static String lakhs(BigDecimal rupees) {
        return rupees.divide(BigDecimal.valueOf(100_000))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String normalize(String query) {
        return query.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
