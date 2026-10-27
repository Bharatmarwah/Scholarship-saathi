package com.bharat.scholarship_rag_backend.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.junit.jupiter.api.Test;

class FaqChunkerTest {

    @Test
    void splitsAnOversizedPairAndRepeatsTheQuestionInEveryPart() {
        TokenCounter counter = new TokenCounter();
        String answer = "The rule applies to every eligible student. ".repeat(400);
        SourceDocument document = new SourceDocument(
                SchemeType.PM_USP_CSSS,
                "pm-usp-csss/faq.md",
                ContentType.FAQ,
                "2025",
                """
                        ## About the FAQ
                        Introductory text.

                        ### 1. What is the rule?
                        Answer: %s
                        """.formatted(answer));

        List<TextChunk> chunks = new FaqChunker(counter).chunk(document);
        List<TextChunk> answerParts = chunks.stream()
                .filter(chunk -> chunk.content().contains("Q: What is the rule?"))
                .toList();

        assertTrue(answerParts.size() > 1, "the oversized answer must be split");
        assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 800),
                "every split part must stay under the hard ceiling");
        for (TextChunk part : answerParts) {
            assertTrue(part.content().contains("Part "),
                    "a split part must identify itself: " + part.content());
            assertFalse(part.content().contains("Questions: "),
                    "a split pair is not a batch of different questions");
        }
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).chunkIndex());
        }
    }
}
