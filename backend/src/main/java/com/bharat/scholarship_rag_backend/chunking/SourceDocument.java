package com.bharat.scholarship_rag_backend.chunking;

import com.bharat.scholarship_rag_backend.entity.ContentType;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;

/**
 * One source file in the corpus.
 *
 * @param scheme       scheme the document belongs to
 * @param documentId   stable identifier, {@code <slug>/<name>}
 * @param contentType  guideline or FAQ; the two are chunked by different rules
 *                     and never merged
 * @param academicYear academic year the document speaks for, recovered from
 *                     its own header text, or {@code null} if it states none
 * @param markdown     full cleaned Markdown source
 */
record SourceDocument(
        SchemeType scheme,
        String documentId,
        ContentType contentType,
        String academicYear,
        String markdown) {

    String slug() {
        int slash = documentId.indexOf('/');
        return slash < 0 ? documentId : documentId.substring(0, slash);
    }

    String fileName() {
        int slash = documentId.indexOf('/');
        return slash < 0 ? documentId : documentId.substring(slash + 1);
    }
}