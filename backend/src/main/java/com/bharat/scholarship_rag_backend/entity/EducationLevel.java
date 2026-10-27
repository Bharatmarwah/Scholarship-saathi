package com.bharat.scholarship_rag_backend.entity;

/**
 * Education stage a scheme targets. This is structural, not an eligibility
 * threshold: it tells the pipeline which profile fields can even apply, so a
 * school-stage scheme is never asked for a college year.
 */
public enum EducationLevel {

    /** Classes 9 and 10. */
    PRE_MATRIC,

    /** Classes 11 and 12. */
    POST_MATRIC,

    /** Class 9 through class 12 completion. */
    SCHOOL,

    /** Undergraduate and postgraduate degree or diploma courses. */
    HIGHER_EDUCATION,

    /** Research level, such as MPhil and PhD. */
    RESEARCH
}