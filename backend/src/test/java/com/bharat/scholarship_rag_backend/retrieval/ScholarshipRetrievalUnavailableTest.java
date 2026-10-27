package com.bharat.scholarship_rag_backend.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.repository.ScholarshipChunkRepo;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.junit.jupiter.api.Test;

class ScholarshipRetrievalUnavailableTest {

    private final ScholarshipChunkRepo chunkRepo = mock(ScholarshipChunkRepo.class);
    private final QueryEmbedding queryEmbedding = mock(QueryEmbedding.class);
    private final ScholarshipRetrieval retrieval =
            new ScholarshipRetrieval(chunkRepo, queryEmbedding, 8, 1.0);

    @Test
    void bothArmsFailingIsAnOutageNotNoEvidence() {
        when(queryEmbedding.embedQuery("q")).thenThrow(new RuntimeException("embedding down"));
        when(chunkRepo.searchByKeyword(anyString(), anyString(), anyBoolean(), anyInt()))
                .thenThrow(new RuntimeException("database down"));

        assertThrows(RetrievalUnavailableException.class,
                () -> retrieval.search("q", List.of(SchemeType.TOP_CLASS_SC)));
    }

    @Test
    void oneWorkingArmStillProducesResults() {
        when(queryEmbedding.embedQuery("q")).thenThrow(new RuntimeException("embedding down"));
        when(chunkRepo.searchByKeyword(anyString(), anyString(), anyBoolean(), anyInt()))
                .thenReturn(List.<Object[]>of(row()));

        List<ScholarshipChunkMatch> matches =
                retrieval.search("q", List.of(SchemeType.TOP_CLASS_SC));

        assertEquals(1, matches.size());
    }

    private static Object[] row() {
        return new Object[] {
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                "TOP_CLASS_SC", "income limit", "GUIDELINE", "g.md", "ELIGIBILITY",
                null, 1, 1, 0, 400, 0.1
        };
    }
}
