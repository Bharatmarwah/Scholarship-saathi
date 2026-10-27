package com.bharat.scholarship_rag_backend.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;

/**
 * Exercises the startup path without a Spring context or a database, which is
 * the whole point: chunking must not depend on Postgres, Redis or any API key.
 */
class CorpusChunkRunnerTest {

    @Test
    @DisplayName("runner chunks the corpus and writes a review dump")
    void chunksCorpusAndWritesDump(@TempDir Path tempDir) throws Exception {
        Path dump = tempDir.resolve("chunk-review.txt");

        CorpusChunkRunner runner = runner(dump, tempDir);
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(dump).exists();

        String written = Files.readString(dump);
        List<TextChunk> chunks = new CorpusChunker().chunkAll();

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.stream().map(TextChunk::documentId).distinct()).hasSize(9);
        assertThat(written).contains("scheme=").contains("tokens=");

        // The dump must describe exactly the chunks that were produced.
        long headers = written.lines().filter(line -> line.startsWith("scheme=")).count();
        assertThat(headers).isEqualTo(chunks.size());
    }

    @Test
    @DisplayName("runner creates missing parent directories for the dump")
    void createsMissingDirectories(@TempDir Path tempDir) {
        Path dump = tempDir.resolve("nested/target/chunk-review.txt");

        runner(dump, tempDir).run(new DefaultApplicationArguments(new String[0]));

        assertThat(dump).exists();
    }

    @Test
    @DisplayName("a second run over an unchanged corpus is skipped")
    void skipsSecondRunOverUnchangedCorpus(@TempDir Path tempDir) throws Exception {
        Path dump = tempDir.resolve("chunk-review.txt");
        Path marker = tempDir.resolve(".corpus-chunked");

        runner(dump, tempDir).run(new DefaultApplicationArguments(new String[0]));
        assertThat(dump).exists();
        String fingerprint = Files.readString(marker);
        assertThat(fingerprint).isNotBlank();

        Files.delete(dump);

        runner(dump, tempDir).run(new DefaultApplicationArguments(new String[0]));

        assertThat(dump).doesNotExist();
    }

    @Test
    @DisplayName("force re-chunks even when the corpus is unchanged")
    void forceRechunks(@TempDir Path tempDir) throws Exception {
        Path dump = tempDir.resolve("chunk-review.txt");
        Path marker = tempDir.resolve(".corpus-chunked");

        runner(dump, tempDir).run(new DefaultApplicationArguments(new String[0]));
        Files.delete(dump);

        new CorpusChunkRunner(new CorpusChunker(), dump.toString(), marker.toString(), true)
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(dump).exists();
    }

    @Test
    @DisplayName("a changed corpus fingerprint triggers a re-chunk")
    void changedFingerprintTriggersRechunk(@TempDir Path tempDir) throws Exception {
        Path dump = tempDir.resolve("chunk-review.txt");
        Path marker = tempDir.resolve(".corpus-chunked");

        runner(dump, tempDir).run(new DefaultApplicationArguments(new String[0]));

        // Simulate the corpus having been edited between runs.
        Files.writeString(marker, "stale-fingerprint", StandardCharsets.UTF_8);
        Files.delete(dump);

        runner(dump, tempDir).run(new DefaultApplicationArguments(new String[0]));

        assertThat(dump).exists();
    }

    @Test
    @DisplayName("fingerprint is stable across calls")
    void fingerprintIsStable() {
        CorpusChunker chunker = new CorpusChunker();
        assertThat(chunker.fingerprint()).isEqualTo(chunker.fingerprint());
    }

    @Test
    @DisplayName("chunking runs by default")
    void enabledByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(ChunkingSlice.class)
                .run(context -> assertThat(context).hasSingleBean(CorpusChunkRunner.class));
    }

    @Test
    @DisplayName("chunking.enabled=false disables the runner")
    void disabledByProperty() {
        new ApplicationContextRunner()
                .withUserConfiguration(ChunkingSlice.class)
                .withPropertyValues("chunking.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CorpusChunkRunner.class));
    }

    private static CorpusChunkRunner runner(Path dump, Path tempDir) {
        return new CorpusChunkRunner(new CorpusChunker(), dump.toString(),
                tempDir.resolve(".corpus-chunked").toString(), false);
    }

    /**
     * Scans the chunking package so {@link CorpusChunkRunner} is registered as
     * a component definition. Registering it programmatically would bypass
     * {@code @ConditionalOnProperty}, which is exactly what these tests check.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = CorpusChunkRunner.class)
    static class ChunkingSlice {

        @Bean
        SchemeRegistry schemeRegistry() {
            return new SchemeRegistry();
        }
    }
}