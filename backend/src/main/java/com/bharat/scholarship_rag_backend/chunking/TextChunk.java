package com.bharat.scholarship_rag_backend.chunking;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;

/**
 * A retrievable span of one source document, carrying everything needed to
 * cite it and to filter on it without joining another table.
 *
 * @param scheme        never mixed across schemes
 * @param documentId    never mixed across documents
 * @param contentType   guideline or FAQ, drives the rerank trust weight
 * @param academicYear  year the document speaks for, may be null
 * @param pageStart     first page the chunk draws text from
 * @param pageEnd       last page; differs from {@code pageStart} whenever a
 *                      section, paragraph or FAQ answer straddles a page break
 * @param section       nearest section heading, null for front matter
 * @param subsection    nearest sub-heading within the section, may be null
 * @param chunkIndex    zero-based position within the document
 * @param tokenCount    real tokenizer count of {@code content}
 * @param content       the text, prefixed with a context header so it reads
 *                      correctly when retrieved on its own
 */
record TextChunk(
        SchemeType scheme,
        String documentId,
        ContentType contentType,
        String academicYear,
        Integer pageStart,
        Integer pageEnd,
        String section,
        String subsection,
        Integer chunkIndex,
        Integer tokenCount,
        String content) {

    boolean spansPages() {
        return pageStart != null && pageEnd != null && !pageStart.equals(pageEnd);
    }
}