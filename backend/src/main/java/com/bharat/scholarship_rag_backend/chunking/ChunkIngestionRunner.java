package com.bharat.scholarship_rag_backend.chunking;

import com.bharat.scholarship_rag_backend.embedding.QueryEmbedding;
import com.bharat.scholarship_rag_backend.entity.ScholarshipChunk;
import com.bharat.scholarship_rag_backend.repository.ScholarshipChunkRepo;
import com.bharat.scholarship_rag_backend.repository.ScholarshipMetadataRepo;
import com.bharat.scholarship_rag_backend.scheme.SchemeMetadataCatalog;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Turns the in-memory {@link TextChunk} set into persisted, embedded
 * {@link ScholarshipChunk} rows.
 *
 * <p>This is the step {@link CorpusChunkRunner} deliberately leaves out: it
 * embeds each chunk with the shared {@code embeddingModel} (the same one used
 * for queries, so vectors stay comparable) and writes the rows to
 * {@code scholarship_chunks}. The parent {@code scholarship_metadata} rows are
 * created on demand from {@link SchemeMetadataCatalog}.
 *
 * <p>Disabled by default so test contexts never call the embedding provider.
 * Run it once with {@code --ingest.enabled=true}. An already populated table is
 * skipped unless {@code --ingest.force=true} is passed.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "ingest", name = "enabled", havingValue = "true")
public class ChunkIngestionRunner implements ApplicationRunner {

    private final CorpusChunker corpusChunker;
    private final QueryEmbedding queryEmbedding;
    private final ScholarshipChunkRepo scholarshipChunkRepo;
    private final ScholarshipMetadataRepo scholarshipMetadataRepo;
    private final SchemeMetadataCatalog schemeMetadataCatalog;
    private final boolean force;

    public ChunkIngestionRunner(
            CorpusChunker corpusChunker,
            QueryEmbedding queryEmbedding,
            ScholarshipChunkRepo scholarshipChunkRepo,
            ScholarshipMetadataRepo scholarshipMetadataRepo,
            SchemeMetadataCatalog schemeMetadataCatalog,
            @Value("${ingest.force:false}") boolean force) {
        this.corpusChunker = corpusChunker;
        this.queryEmbedding = queryEmbedding;
        this.scholarshipChunkRepo = scholarshipChunkRepo;
        this.scholarshipMetadataRepo = scholarshipMetadataRepo;
        this.schemeMetadataCatalog = schemeMetadataCatalog;
        this.force = force;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        syncMetadataRows();

        long existing = scholarshipChunkRepo.count();
        if (!force && existing > 0) {
            log.info("scholarship_chunks already holds {} rows; skipping ingestion. "
                    + "Pass --ingest.force=true to re-embed and replace it.", existing);
            return;
        }
        if (force && existing > 0) {
            log.info("Force ingest: clearing {} existing scholarship_chunks rows", existing);
            scholarshipChunkRepo.deleteAllInBatch();
        }

        long started = System.currentTimeMillis();
        List<TextChunk> chunks = corpusChunker.chunkAll();

        log.info("Embedding {} chunks (batched)...", chunks.size());
        List<float[]> embeddings = queryEmbedding.embedAll(
                chunks.stream().map(TextChunk::content).toList());

        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException(
                    "Embedding provider returned "
                            + (embeddings == null ? "no" : embeddings.size())
                            + " vectors for " + chunks.size()
                            + " chunks; refusing to persist a partial corpus");
        }

        for (int i = 0; i < chunks.size(); i++) {
            scholarshipChunkRepo.save(toEntity(chunks.get(i), embeddings.get(i)));
            if ((i + 1) % 25 == 0) {
                log.info("Persisted {}/{} chunks", i + 1, chunks.size());
            }
        }

        log.info("Ingestion complete: saved {} chunks in {} ms",
                chunks.size(), System.currentTimeMillis() - started);
    }

    private void syncMetadataRows() {
        // The catalog is authoritative. Refresh every scheme before the
        // chunk-count check so verified metadata reaches an existing database
        // without forcing an expensive re-embed of the corpus.
        for (SchemeType scheme : SchemeType.values()) {
            scholarshipMetadataRepo.save(schemeMetadataCatalog.forScheme(scheme));
        }
    }

    private ScholarshipChunk toEntity(TextChunk chunk, float[] embedding) {
        return ScholarshipChunk.builder()
                .metadata(scholarshipMetadataRepo.getReferenceById(chunk.scheme()))
                .scheme(chunk.scheme())
                .content(chunk.content())
                .contentType(chunk.contentType())
                .embedding(embedding)
                .sourceDocument(chunk.documentId())
                .sourceSection(chunk.section())
                .sourceSubsection(chunk.subsection())
                .pageStart(chunk.pageStart())
                .pageEnd(chunk.pageEnd())
                .chunkIndex(chunk.chunkIndex() == null ? 0 : chunk.chunkIndex())
                .tokenCount(chunk.tokenCount())
                .contentHash(sha256Hex(chunk.content()))
                .createdAt(Instant.now())
                .build();
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
