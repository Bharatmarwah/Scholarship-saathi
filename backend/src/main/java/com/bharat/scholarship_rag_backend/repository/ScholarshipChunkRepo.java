package com.bharat.scholarship_rag_backend.repository;

import java.util.List;
import java.util.UUID;

import com.bharat.scholarship_rag_backend.entity.ScholarshipChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read access to the knowledge base.
 *
 * <p>Both search methods return {@code Object[]} rather than a projection.
 * Native queries bypass the JPA attribute converter, so an {@code @Enumerated}
 * column arrives as a raw {@code String}; mapping the rows explicitly keeps the
 * {@code SchemeType} and {@code ContentType} conversions in one visible place
 * instead of hiding them behind a constructor that may or may not bind.
 *
 * <p>The embedding and the scheme filter are passed as PostgreSQL literal text
 * ({@code "[0.1,0.2]"} and {@code "{SCHEME_A,SCHEME_B}"}) rather than as Java
 * arrays. {@code CAST(float8[] AS vector)} is not a valid cast, so binding the
 * array directly would fail at runtime; the literal form is what pgvector
 * accepts.
 *
 * <p>{@code :scoped} exists so a single query serves both the named-scheme and
 * the discovery case. An empty scheme list therefore yields
 * {@code scheme = ANY('{}')}, which is false for every row, instead of the
 * {@code IN ()} syntax error an empty collection expands to.
 */
public interface ScholarshipChunkRepo extends JpaRepository<ScholarshipChunk, UUID> {

    /**
     * Semantic nearest neighbours, closest first.
     *
     * @param queryEmbedding pgvector literal, for example {@code "[0.1,0.2]"}
     * @param schemes        pgvector-style array literal of scheme names, may be
     *                       empty when {@code scoped} is false
     * @param scoped         whether {@code schemes} should filter results
     * @param maxDistance    cosine distance cut-off in {@code [0, 2]}; 2.0 keeps
     *                       every row (gate off), lower values are stricter
     * @param topK           maximum rows returned
     */
    @Query(value = """
            SELECT c.id,
                   c.scheme,
                   c.content,
                   c.content_type,
                   c.source_document,
                   c.source_section,
                   c.source_subsection,
                   c.page_start,
                   c.page_end,
                   c.chunk_index,
                   c.token_count,
                   (c.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
            FROM scholarship_chunks c
            WHERE c.embedding IS NOT NULL
              AND (:scoped = false
                   OR CAST(c.scheme AS text) = ANY(CAST(:schemes AS text[])))
              AND (c.embedding <=> CAST(:queryEmbedding AS vector)) <= :maxDistance
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> searchByVector(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("schemes") String schemes,
            @Param("scoped") boolean scoped,
            @Param("maxDistance") double maxDistance,
            @Param("topK") int topK);

    /**
     * Full-text matches on the generated {@code search_vector}, best rank first.
     *
     * <p>{@code websearch_to_tsquery} is used rather than {@code to_tsquery}
     * because it never raises on odd user input; a malformed query would
     * otherwise turn a search into a 500.
     */
    @Query(value = """
            SELECT c.id,
                   c.scheme,
                   c.content,
                   c.content_type,
                   c.source_document,
                   c.source_section,
                   c.source_subsection,
                   c.page_start,
                   c.page_end,
                   c.chunk_index,
                   c.token_count,
                   ts_rank(c.search_vector,
                           websearch_to_tsquery('english', :query)) AS keyword_rank
            FROM scholarship_chunks c
            WHERE c.search_vector IS NOT NULL
              AND c.search_vector @@ websearch_to_tsquery('english', :query)
              AND (:scoped = false
                   OR CAST(c.scheme AS text) = ANY(CAST(:schemes AS text[])))
            ORDER BY keyword_rank DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> searchByKeyword(
            @Param("query") String query,
            @Param("schemes") String schemes,
            @Param("scoped") boolean scoped,
            @Param("topK") int topK);
}