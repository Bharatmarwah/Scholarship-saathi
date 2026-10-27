package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Typed result of the validation step. This is NOT an eligibility verdict.
 * Only {@link ValidationStatus#READY} or
 * {@link ValidationStatus#MISSING_INFORMATION} is produced here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private ValidationMode mode;

    private ValidationStatus status;

    private SchemeType targetScheme;

    private List<SchemeType> targetSchemes;

    private List<StudentProfileField> missingFields;

    private String question;

    private String reason;

    public static ValidationResult ready(
            ValidationMode mode,
            SchemeType targetScheme,
            List<SchemeType> targetSchemes) {
        return new ValidationResult(mode, ValidationStatus.READY,
                targetScheme, targetSchemes, List.of(), null, null);
    }

    public static ValidationResult missing(
            ValidationMode mode,
            SchemeType targetScheme,
            List<SchemeType> targetSchemes,
            List<StudentProfileField> missingFields,
            String question,
            String reason) {
        return new ValidationResult(mode, ValidationStatus.MISSING_INFORMATION,
                targetScheme, targetSchemes, missingFields, question, reason);
    }
}