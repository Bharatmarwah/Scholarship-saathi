package com.bharat.scholarship_rag_backend.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.junit.jupiter.api.Test;

/**
 * Covers the pure logic of {@link ScholarshipRetrieval}: how a native row is
 * mapped and how the two result lists are fused. No database is involved.
 */
class ScholarshipRetrievalFusionTest {

    private final ScholarshipRetrieval retrieval =
            new ScholarshipRetrieval(null, null, 8, 0.8);

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    /**
     * Builds one row in the column order the native query selects.
     * {@code List.of(row(...))} would not work here: a single {@code Object[]}
     * passed to {@code List.of} is spread as varargs into a {@code List<Object>}.
     */
    private static Object[] row(UUID id,
                                String scheme,
                                String content,
                                String contentType,
                                String document,
                                String section,
                                Integer pageStart,
                                Integer pageEnd,
                                Integer chunkIndex,
                                Double score) {
        return new Object[] {
                id, scheme, content, contentType, document, section, null,
                pageStart, pageEnd, chunkIndex, 400, score};
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    @Test
    void mapsNativeRowIncludingEnumAndScoreColumns() {
        ScholarshipChunkMatch match = retrieval.map(
                row(A, "TOP_CLASS_PWD", "Annual family income must not exceed Rs. 8 lakh.",
                        "GUIDELINE", "top-class-pwd/guideline.md", "4. ELIGIBILITY",
                        3, 4, 7, 0.21),
                true);

        assertNotNull(match);
        assertEquals(A, match.id());
        assertEquals(SchemeType.TOP_CLASS_PWD, match.scheme());
        assertEquals(ContentType.GUIDELINE, match.contentType());
        assertEquals("top-class-pwd/guideline.md", match.sourceDocument());
        assertEquals("4. ELIGIBILITY", match.sourceSection());
        assertEquals(7, match.chunkIndex());
        assertEquals(400, match.tokenCount());
        assertEquals(0.21, match.distance());
        assertNull(match.keywordRank());
        assertTrue(match.fromVector());
        assertFalse(match.fromKeyword());
        assertEquals("pages 3-4", match.pageLabel());
    }

    @Test
    void keywordRowsLandInTheKeywordColumn() {
        ScholarshipChunkMatch match = retrieval.map(
                row(A, "ISHAN_UDAY", "Two siblings may receive the benefit.",
                        "FAQ", "ishan-uday/faq.md", null, 2, 2, 1, 0.09),
                false);

        assertEquals(0.09, match.keywordRank());
        assertNull(match.distance());
        assertTrue(match.fromKeyword());
        assertFalse(match.fromVector());
        assertEquals("page 2", match.pageLabel());
    }

    @Test
    void acceptsNumericAndStringFormsOfTheSameColumns() {
        // Drivers are free to hand back Integer, Long or String for a numeric
        // column; all three have to land on the same value.
        Object[] mixed = {
                "00000000-0000-0000-0000-0000000000a1", "ISHAN_UDAY", "text", "FAQ",
                "f.md", "section", null, 3, "4", 2L, 400, "0.5"};

        ScholarshipChunkMatch match = retrieval.map(mixed, true);

        assertEquals(A, match.id());
        assertEquals(3, match.pageStart());
        assertEquals(4, match.pageEnd());
        assertEquals(2, match.chunkIndex());
        assertEquals(0.5, match.distance());
    }

    @Test
    void unparsableNumericCellsBecomeNullRatherThanThrowing() {
        Object[] odd = {
                A, "ISHAN_UDAY", "text", "FAQ", "f.md", "section", null,
                "not-a-page", "four", "2L", 400, "n/a"};

        ScholarshipChunkMatch match = retrieval.map(odd, true);

        assertNotNull(match, "a bad cell must not discard the whole row");
        assertNull(match.pageStart());
        assertNull(match.pageEnd());
        assertNull(match.chunkIndex());
        assertNull(match.distance());
    }

    @Test
    void rejectsRowsWithBlankContentOrWrongArity() {
        assertNull(retrieval.map(
                row(A, "ISHAN_UDAY", "   ", "FAQ", "d.md", null, 1, 1, 0, 0.1), true));
        assertNull(retrieval.map(new Object[] {A, "ISHAN_UDAY"}, true));
        assertNull(retrieval.map(null, true));
    }

    @Test
    void unknownEnumValuesDegradeInsteadOfThrowing() {
        // A row written by a future version must not break search entirely.
        assertNull(retrieval.map(
                row(A, "SOMETHING_NEW", "text", "FAQ", "d.md", null, 1, 1, 0, 0.1),
                true).scheme());

        assertEquals(ContentType.GUIDELINE, retrieval.map(
                row(A, "ISHAN_UDAY", "text", "MYSTERY", "d.md", null, 1, 1, 0, 0.1),
                true).contentType());

        assertNull(ScholarshipRetrieval.scheme("  "));
        assertNull(ScholarshipRetrieval.scheme(null));
        assertEquals(SchemeType.TOP_CLASS_SC, ScholarshipRetrieval.scheme("top_class_sc"));
    }

    @Test
    void chunkFoundByBothArmsOutranksOneFoundByASingleArm() {
        List<ScholarshipChunkMatch> fused = retrieval.fuse(
                rows(row(A, "ISHAN_UDAY", "a", "GUIDELINE", "g.md", "s", 1, 1, 0, 0.1),
                        row(B, "ISHAN_UDAY", "b", "GUIDELINE", "g.md", "s", 1, 1, 1, 0.2)),
                rows(row(A, "ISHAN_UDAY", "a", "GUIDELINE", "g.md", "s", 1, 1, 0, 0.5)));

        assertEquals(2, fused.size());
        assertEquals(A, fused.get(0).id(), "agreement between arms must win");
        assertTrue(fused.get(0).score() > fused.get(1).score());
    }

    @Test
    void guidelineOutranksIdenticallyRankedFaq() {
        List<ScholarshipChunkMatch> guideline = retrieval.fuse(
                rows(row(A, "ISHAN_UDAY", "normative", "GUIDELINE", "g.md", "s", 1, 1, 0, 0.1)),
                List.of());
        List<ScholarshipChunkMatch> faq = retrieval.fuse(
                rows(row(A, "ISHAN_UDAY", "restated", "FAQ", "f.md", "s", 1, 1, 0, 0.1)),
                List.of());

        assertEquals(1.0 / 61, guideline.get(0).score(), 1e-9,
                "an unopposed first-ranked guideline hit scores exactly one reciprocal rank");
        assertEquals(0.9 / 61, faq.get(0).score(), 1e-9);
        assertTrue(guideline.get(0).score() > faq.get(0).score(),
                "the guideline must outrank the FAQ restatement of it");
    }

    @Test
    void identicalContentStoredInDifferentRowsIsReturnedOnce() {
        List<ScholarshipChunkMatch> fused = retrieval.fuse(
                rows(row(A, "ISHAN_UDAY", "The income ceiling is eight lakh.",
                                "GUIDELINE", "g.md", "s", 1, 1, 0, 0.1),
                        row(B, "ISHAN_UDAY", "The income ceiling is eight lakh.",
                                "ANNEXURE", "a.md", "s", 2, 2, 1, 0.2)),
                List.of());

        assertEquals(1, fused.size(),
                "the same passage repeated in an annexure must not be sent twice");
    }

    @Test
    void duplicateAppearingInBothArmsIsReturnedOnce() {
        List<ScholarshipChunkMatch> fused = retrieval.fuse(
                rows(row(A, "ISHAN_UDAY", "a", "GUIDELINE", "g.md", "s", 1, 1, 0, 0.1)),
                rows(row(A, "ISHAN_UDAY", "a", "GUIDELINE", "g.md", "s", 1, 1, 0, 0.5)));

        assertEquals(1, fused.size());
        assertTrue(fused.get(0).fromVector(),
                "the vector arm is absorbed first, so its row supplies the score column");
        assertNull(fused.get(0).keywordRank());
        assertEquals(1.0 / 61 + 1.0 / 61, fused.get(0).score(), 1e-9,
                "both arms contribute even though the chunk is returned once");
    }

    @Test
    void clampsMaxDistanceToTheCosineRange() {
        assertEquals(2.0, ScholarshipRetrieval.clampDistance(5), 1e-12);
        assertEquals(0.0, ScholarshipRetrieval.clampDistance(-1), 1e-12);
        assertEquals(1.2, ScholarshipRetrieval.clampDistance(1.2), 1e-12);
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertTrue(retrieval.fuse(List.of(), List.of()).isEmpty());
    }

    @Test
    void rowsWithoutAUsableIdAreDroppedRatherThanFused() {
        List<ScholarshipChunkMatch> fused = retrieval.fuse(
                rows(row(null, "ISHAN_UDAY", "no id", "GUIDELINE", "g.md", "s", 1, 1, 0, 0.1),
                        row(B, "ISHAN_UDAY", "good", "GUIDELINE", "g.md", "s", 1, 1, 1, 0.2)),
                List.of());

        assertEquals(1, fused.size());
        assertEquals(B, fused.get(0).id());
    }

    @Test
    void citationNamesSchemeSectionAndPage() {
        List<ScholarshipChunkMatch> fused = retrieval.fuse(
                rows(row(C, "TOP_CLASS_PWD", "c", "GUIDELINE",
                        "top-class-pwd/guideline.md", "9. SELECTION", 12, 12, 3, 0.1)),
                List.of());

        assertEquals("TOP CLASS PWD, 9. SELECTION, page 12", fused.get(0).citation());
    }

    @Test
    void citationOmitsMissingPartsRatherThanPrintingNull() {
        List<ScholarshipChunkMatch> fused = retrieval.fuse(
                rows(row(C, "ISHAN_UDAY", "c", "FAQ", "f.md", null, null, null, 0, 0.1)),
                List.of());

        String citation = fused.get(0).citation();
        assertFalse(citation.contains("null"), "citation must not leak nulls: " + citation);
    }

    @Test
    void equalScoresBreakTiesOnProvenanceNotInsertionOrder() {
        // One hit ranked first by each arm: both score exactly 1/(60+1), so the
        // tie-break alone decides the order and that decision must not depend on
        // which array the row arrived in.
        List<ScholarshipChunkMatch> fromVectorFirst = retrieval.fuse(
                rows(row(A, "ISHAN_UDAY", "a", "GUIDELINE", "b.md", "s", 1, 1, 0, 0.1)),
                rows(row(B, "ISHAN_UDAY", "b", "GUIDELINE", "a.md", "s", 1, 1, 0, 0.2)));
        List<ScholarshipChunkMatch> swapped = retrieval.fuse(
                rows(row(B, "ISHAN_UDAY", "b", "GUIDELINE", "a.md", "s", 1, 1, 0, 0.2)),
                rows(row(A, "ISHAN_UDAY", "a", "GUIDELINE", "b.md", "s", 1, 1, 0, 0.1)));

        assertEquals(0.0, fromVectorFirst.get(0).score() - fromVectorFirst.get(1).score(), 1e-12,
                "the fixture must produce a genuine tie");
        assertEquals(fromVectorFirst.stream().map(ScholarshipChunkMatch::id).toList(),
                swapped.stream().map(ScholarshipChunkMatch::id).toList());
        assertEquals(B, fromVectorFirst.get(0).id(),
                "document a.md sorts before b.md, so its chunk must come first");
    }

    @Test
    void repeatedFusionOfTheSameInputIsStable() {
        List<Object[]> vectorRows = rows(
                row(A, "ISHAN_UDAY", "a", "GUIDELINE", "g.md", "s", 1, 1, 0, 0.1),
                row(B, "ISHAN_UDAY", "b", "GUIDELINE", "g.md", "s", 1, 1, 1, 0.2));
        List<Object[]> keywordRows = rows(
                row(C, "ISHAN_UDAY", "c", "FAQ", "f.md", "s", 1, 1, 0, 0.3));

        assertEquals(
                retrieval.fuse(vectorRows, keywordRows).stream()
                        .map(ScholarshipChunkMatch::id).toList(),
                retrieval.fuse(vectorRows, keywordRows).stream()
                        .map(ScholarshipChunkMatch::id).toList());
    }
}