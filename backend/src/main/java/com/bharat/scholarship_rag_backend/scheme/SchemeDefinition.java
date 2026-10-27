package com.bharat.scholarship_rag_backend.scheme;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Configuration of which {@link StudentProfileField} values a
 * scheme needs to be validated. This is NOT a JPA entity and does not map
 * to a database table.
 *
 * <p>It only describes required <em>information</em>. Numeric eligibility
 * thresholds (income ceilings, percentile cut-offs, category/domicile sets)
 * are NOT stored here; they belong to the future EligibilityEngine.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemeDefinition {

    private SchemeType scheme;

    private Set<StudentProfileField> discoveryFields;

    private Set<StudentProfileField> essentialFields;

    private Set<StudentProfileField> conditionalFields;

    private Set<StudentProfileField> selectionFields;
}