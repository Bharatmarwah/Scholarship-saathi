package com.bharat.scholarship_rag_backend.chunking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Chunks the whole corpus during startup, <strong>once</strong>.
 *
 * <p>Runs as an {@link ApplicationRunner} rather than {@code @PostConstruct}
 * because the context must be fully refreshed first: a runner executes after
 * every bean exists, whereas {@code @PostConstruct} fires during bean creation
 * when other beans may still be initialising and cannot be switched off
 * cleanly.
 *
 * <p>Repeated startups must not repeat the work, so the corpus digest is
 * compared against a marker written by the previous run. An unchanged corpus is
 * skipped; an edited one is re-chunked automatically. To redo it deliberately,
 * pass {@code --chunking.force=true} or delete the marker.
 *
 * <p>Enabled by default and switched off entirely with
 * {@code --chunking.enabled=false}.
 *
 * <p><strong>No embedding and no database writes happen here.</strong> This only
 * produces the reviewable chunk set. Persistence is a separate step that needs
 * the pgvector schema to exist first.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "chunking", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CorpusChunkRunner implements ApplicationRunner {

    private final CorpusChunker corpusChunker;
    private final String output;
    private final String marker;
    private final boolean force;

    public CorpusChunkRunner(
            CorpusChunker corpusChunker,
            @Value("${chunking.output:.chunking/chunk-review.txt}") String output,
            @Value("${chunking.marker:.chunking/.corpus-chunked}") String marker,
            @Value("${chunking.force:false}") boolean force) {
        this.corpusChunker = corpusChunker;
        this.output = output;
        this.marker = marker;
        this.force = force;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path markerFile = Path.of(marker);
        String current = corpusChunker.fingerprint();

        if (!force && isUpToDate(markerFile, current)) {
            log.info("Corpus is unchanged since the last run, so chunking was skipped. "
                    + "The existing {} is still current.", Path.of(output).toAbsolutePath());
            log.info("Pass --chunking.force=true to chunk again anyway.");
            return;
        }

        long started = System.nanoTime();

        List<TextChunk> chunks = corpusChunker.chunkAll();
        CorpusChunker.ChunkingReport report = new CorpusChunker.ChunkingReport(chunks);

        Path dump = corpusChunker.writeReviewDump(chunks, Path.of(output));
        writeMarker(markerFile, current);

        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("Chunked the scholarship corpus in {} ms: {}", millis, report.summary());
        log.info("Corpus chunk report:\n{}", report.render());
        log.info("Chunk review dump written to {}", dump.toAbsolutePath());
        log.info("Marked as chunked in {}. The next start will skip this unless the corpus changes.",
                markerFile.toAbsolutePath());
        log.info("No embeddings were generated and nothing was written to the database.");
    }

    /** A readable or missing marker simply means the corpus must be chunked. */
    private boolean isUpToDate(Path markerFile, String current) {
        if (!Files.exists(markerFile)) {
            return false;
        }
        try {
            return current.equals(Files.readString(markerFile, StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            log.warn("Could not read the chunking marker at {}; re-chunking. Cause: {}",
                    markerFile.toAbsolutePath(), e.getMessage());
            return false;
        }
    }

    private void writeMarker(Path markerFile, String fingerprint) {
        try {
            Path parent = markerFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(markerFile, fingerprint, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write the chunking marker to " + markerFile, e);
        }
    }
}