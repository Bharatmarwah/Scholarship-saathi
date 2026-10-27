package com.bharat.scholarship_rag_backend.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the real chunkers over the real corpus and asserts the properties the
 * retrieval layer depends on.
 *
 * <p>These are the guarantees that are easy to break with a refactor and
 * expensive to notice later: a chunk over the embedding model's practical
 * limit, a chunk with no provenance, or a document whose indexes restart.
 */
class ChunkerCorpusInvariantsTest {

    /** Practical ceiling; the embedding model and pgvector cope comfortably to here. */
    private static final int CEILING = 800;

    private static List<TextChunk> chunks;

    @BeforeAll
    static void chunkCorpus() {
        chunks = new CorpusChunker().chunkAll();
    }

    @Test
    void corpusProducesChunksForEveryDocument() {
        Map<String, Integer> perDocument = new LinkedHashMap<>();
        for (TextChunk chunk : chunks) {
            perDocument.merge(chunk.documentId(), 1, Integer::sum);
        }

        assertEquals(9, perDocument.size(), "expected all nine corpus documents");
        assertTrue(perDocument.values().stream().noneMatch(count -> count == 0),
                "no document may produce zero chunks");
    }

    @Test
    void everyChunkCarriesTheMetadataRetrievalNeeds() {
        for (TextChunk chunk : chunks) {
            String where = chunk.documentId() + "#" + chunk.chunkIndex();
            assertNotNull(chunk.scheme(), where);
            assertNotNull(chunk.documentId(), where);
            assertNotNull(chunk.contentType(), where);
            assertNotNull(chunk.chunkIndex(), where);
            assertNotNull(chunk.tokenCount(), where);
            assertFalse(chunk.content() == null || chunk.content().isBlank(), where);
            assertTrue(chunk.pageStart() != null, where + " needs a start page");
            assertTrue(chunk.pageEnd() != null, where + " needs an end page");
            assertTrue(chunk.pageStart() <= chunk.pageEnd(), where + " pages must not run backwards");
        }
    }

    @Test
    void noChunkExceedsTheCeiling() {
        List<String> oversized = chunks.stream()
                .filter(chunk -> chunk.tokenCount() > CEILING)
                .map(chunk -> chunk.documentId() + "#" + chunk.chunkIndex()
                        + " tokens=" + chunk.tokenCount())
                .toList();

        assertTrue(oversized.isEmpty(), "chunks over the ceiling: " + oversized);
    }

    @Test
    void recordedTokenCountMatchesTheRealTokenizer() {
        TokenCounter counter = new TokenCounter();
        List<String> mismatches = chunks.stream()
                .filter(chunk -> !Objects.equals(
                        counter.count(chunk.content()), chunk.tokenCount()))
                .map(chunk -> chunk.documentId() + "#" + chunk.chunkIndex()
                        + " stored=" + chunk.tokenCount()
                        + " actual=" + counter.count(chunk.content()))
                .toList();

        assertTrue(mismatches.isEmpty(), "stored token counts drifted: " + mismatches);
    }

    @Test
    void chunkIndexesAreSequentialWithinEachDocument() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (TextChunk chunk : chunks) {
            int next = expected.getOrDefault(chunk.documentId(), 0);
            assertEquals(next, chunk.chunkIndex(),
                    chunk.documentId() + " indexes must run 0,1,2 without gaps or repeats");
            expected.put(chunk.documentId(), next + 1);
        }
    }

    @Test
    void guidelinesAndFaqsAreNeverMixedInOneChunk() {
        // The FAQ documents name themselves in the chunk header. A guideline
        // chunk carrying the FAQ marker would mean the two rule sets were merged.
        List<String> mixed = chunks.stream()
                .filter(chunk -> chunk.contentType() == ContentType.GUIDELINE)
                .filter(chunk -> chunk.content().contains("| FAQ"))
                .map(chunk -> chunk.documentId() + "#" + chunk.chunkIndex())
                .toList();

        assertTrue(mixed.isEmpty(), "guideline chunks must not carry FAQ text: " + mixed);
    }

    @Test
    void everyChunkOpensWithAContextHeaderNamingItsScheme() {
        List<String> missing = chunks.stream()
                .filter(chunk -> !chunk.content().startsWith(
                        chunk.scheme().name().replace('_', ' ')))
                .map(chunk -> chunk.documentId() + "#" + chunk.chunkIndex()
                        + " -> " + chunk.content().lines().findFirst().orElse(""))
                .toList();

        assertTrue(missing.isEmpty(),
                "chunks must be standalone-readable with the scheme named: " + missing);
    }

    @Test
    void faqPairsAreNotStrandedWithoutTheirQuestion() {
        // A multi-question FAQ chunk lists its questions on a "Questions:" line.
        // Nothing should be left with a bare answer and no question at all.
        List<String> stranded = chunks.stream()
                .filter(chunk -> chunk.contentType() == ContentType.FAQ)
                .filter(chunk -> chunk.chunkIndex() > 0)
                .filter(chunk -> !chunk.content().contains("Q: ")
                        && !chunk.content().contains("Questions: "))
                .map(chunk -> chunk.documentId() + "#" + chunk.chunkIndex())
                .toList();

        assertTrue(stranded.isEmpty(), "FAQ chunks without a question: " + stranded);
    }

    @Test
    void distributionStaysInTheUsefulMiddle() {
        int size = chunks.size();
        long tiny = chunks.stream().filter(c -> c.tokenCount() < 40).count();
        long huge = chunks.stream().filter(c -> c.tokenCount() > CEILING).count();

        assertTrue(size > 50, "expected a substantial chunk count, got " + size);
        assertTrue(tiny * 10 <= size,
                "too many scraps under 40 tokens: " + tiny + " of " + size);
        assertEquals(0, huge);
    }
}