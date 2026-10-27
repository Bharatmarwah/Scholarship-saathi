package com.bharat.scholarship_rag_backend.chunking;

import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.entity.ScholarshipChunk;
import com.bharat.scholarship_rag_backend.entity.ScholarshipMetadata;
import com.bharat.scholarship_rag_backend.repository.ScholarshipChunkRepo;
import com.bharat.scholarship_rag_backend.repository.ScholarshipMetadataRepo;
import com.bharat.scholarship_rag_backend.scheme.SchemeMetadataCatalog;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkIngestionRunnerTest {

    private final CorpusChunker corpusChunker = mock(CorpusChunker.class);
    private final QueryEmbedding queryEmbedding = mock(QueryEmbedding.class);
    private final ScholarshipChunkRepo chunkRepo = mock(ScholarshipChunkRepo.class);
    private final ScholarshipMetadataRepo metadataRepo = mock(ScholarshipMetadataRepo.class);
    private final SchemeMetadataCatalog catalog = new SchemeMetadataCatalog();

    private TextChunk chunk() {
        return new TextChunk(
                SchemeType.PM_USP_CSSS,
                "pm-usp-csss/guideline.md",
                ContentType.GUIDELINE,
                "2025",
                1,
                2,
                "Eligibility",
                null,
                0,
                42,
                "PM-USP CSSS | Guideline | Eligibility\nThe income ceiling is eight lakh.");
    }

    @Test
    void embedsEachChunkAndPersistsItWithItsScheme() {
        when(chunkRepo.count()).thenReturn(0L);
        when(corpusChunker.chunkAll()).thenReturn(List.of(chunk()));
        when(queryEmbedding.embedAll(anyList())).thenReturn(List.of(new float[]{0.5f, 0.25f}));

        ScholarshipMetadata managed = catalog.forScheme(SchemeType.PM_USP_CSSS);
        when(metadataRepo.getReferenceById(SchemeType.PM_USP_CSSS)).thenReturn(managed);

        new ChunkIngestionRunner(corpusChunker, queryEmbedding, chunkRepo, metadataRepo, catalog, false)
                .run(null);

        verify(metadataRepo, times(SchemeType.values().length))
                .save(any(ScholarshipMetadata.class));

        ArgumentCaptor<ScholarshipChunk> captor = ArgumentCaptor.forClass(ScholarshipChunk.class);
        verify(chunkRepo).save(captor.capture());
        ScholarshipChunk saved = captor.getValue();

        assertEquals(SchemeType.PM_USP_CSSS, saved.getScheme());
        assertEquals(ContentType.GUIDELINE, saved.getContentType());
        assertEquals("pm-usp-csss/guideline.md", saved.getSourceDocument());
        assertEquals(0, saved.getChunkIndex());
        assertEquals(42, saved.getTokenCount());
        assertNotNull(saved.getContentHash());
        assertArrayEquals(new float[]{0.5f, 0.25f}, saved.getEmbedding());
    }

    @Test
    void refusesToPersistWhenTheEmbeddingProviderReturnsFewerVectors() {
        when(chunkRepo.count()).thenReturn(0L);
        when(corpusChunker.chunkAll()).thenReturn(List.of(chunk(), chunk()));
        when(queryEmbedding.embedAll(anyList())).thenReturn(List.of(new float[]{0.5f}));

        ChunkIngestionRunner runner = new ChunkIngestionRunner(
                corpusChunker, queryEmbedding, chunkRepo, metadataRepo, catalog, false);

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(chunkRepo, never()).save(any());
    }

    @Test
    void refreshesMetadataButSkipsChunksWhenChunksAlreadyExistAndNotForced() {
        when(chunkRepo.count()).thenReturn(10L);

        new ChunkIngestionRunner(corpusChunker, queryEmbedding, chunkRepo, metadataRepo, catalog, false)
                .run(null);

        verify(metadataRepo, times(SchemeType.values().length))
                .save(any(ScholarshipMetadata.class));
        verify(corpusChunker, never()).chunkAll();
        verify(queryEmbedding, never()).embed(anyString());
        verify(queryEmbedding, never()).embedAll(anyList());
        verify(chunkRepo, never()).save(any());
    }
}
