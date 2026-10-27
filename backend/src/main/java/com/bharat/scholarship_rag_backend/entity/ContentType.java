package com.bharat.scholarship_rag_backend.entity;

/**
 * What kind of source a chunk was cut from.
 *
 * <p>Guideline and FAQ chunks live in the same table and share one embedding
 * index, but they are not equally authoritative. The guideline is the
 * normative text; the FAQ is a restatement of it. Retrieval recalls both and
 * applies {@code ContentType} as a tie-break weight, so a guideline passage
 * outranks an FAQ answer that says the same thing. Keeping the distinction in
 * a column is what makes that possible without a second table.
 */
public enum ContentType {

    /** Normative scheme text: eligibility, benefits, application, renewal. */
    GUIDELINE,

    /** Question and answer pair lifted from an official FAQ document. */
    FAQ,

    /** Landing or scheme page scraped from a government portal. */
    PORTAL_PAGE,

    /** Table, annexure or schedule reproduced from a guideline. */
    ANNEXURE
}