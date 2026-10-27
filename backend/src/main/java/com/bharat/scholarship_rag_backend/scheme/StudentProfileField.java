package com.bharat.scholarship_rag_backend.scheme;

/**
 * Full student profile taxonomy, grouped by domain. Values are used by the
 * scheme definitions, the query composer, the question builder and the
 * profile enrichment step.
 *
 * <p>No scheme uses every field. Each {@link SchemeDefinition} selects the
 * fields it needs and classifies them as discovery, essential, conditional or
 * selection. Numeric eligibility thresholds (income ceilings, percentiles,
 * disability cut-offs) are intentionally NOT part of this enum.
 *
 * <p>Some fields are scheme-relevant today but do not yet map to a
 * {@code StudentProfile} column: {@code STUDY_LEVEL}, {@code COURSE_TYPE},
 * {@code ADMISSION_RANK},
 * {@code HAS_VALID_DISABILITY_CERTIFICATE}, {@code HAS_UDID_OR_UDID_ENROLLMENT},
 * {@code SIBLINGS_RECEIVING_BENEFIT}, {@code GENDER}, {@code DATE_OF_BIRTH},
 * {@code STREAM}, {@code CLASS12_PASSED_YEAR} and {@code DISABILITY_TYPE}.
 * They are read as missing until the profile stores them.
 */
public enum StudentProfileField {

    // Education
    EDUCATION_LEVEL,
    COURSE,
    COURSE_TYPE,
    CURRENT_YEAR,
    CURRENT_CLASS,
    REGULAR_MODE,
    STUDY_LEVEL,
    HAS_PRIOR_DEGREE,
    CLASS12_PERCENTILE,
    CLASS12_PASSED_YEAR,
    BOARD,
    STREAM,
    PREVIOUS_CLASS_MARKS,
    APPLICATION_TYPE,
    ADMISSION_RANK,

    // Personal
    NATIONALITY,
    GENDER,
    DATE_OF_BIRTH,

    // Financial
    ANNUAL_FAMILY_INCOME,

    // Social and domicile
    SOCIAL_CATEGORY,
    DOMICILE_STATE,

    // Institution
    INSTITUTION_NAME,
    INSTITUTION_TYPE,

    // Existing scholarship
    RECEIVING_OTHER_SCHOLARSHIP,
    SIBLINGS_RECEIVING_BENEFIT,

    // Disability
    HAS_DISABILITY,
    DISABILITY_PERCENTAGE,
    DISABILITY_TYPE,
    HAS_VALID_DISABILITY_CERTIFICATE,
    HAS_UDID_OR_UDID_ENROLLMENT
}