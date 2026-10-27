package com.bharat.scholarship_rag_backend.scheme;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for reading a {@link StudentProfileField} value off
 * the {@link StudentProfileDto} and deciding whether the field is present.
 *
 * <p>Presence rules (no generic truthiness):
 * <ul>
 *   <li>null → missing</li>
 *   <li>blank String → missing</li>
 *   <li>empty collection → missing</li>
 *   <li>Boolean false → PRESENT (we know it is not true)</li>
 *   <li>numeric 0 / 0.0 → PRESENT</li>
 * </ul>
 */
@Component
public class ProfileFieldAccess {

    public boolean isPresent(StudentProfileDto profile, StudentProfileField field) {
        if (profile == null || field == null) {
            return false;
        }
        return isPresentValue(read(profile, field));
    }

    public Object read(StudentProfileDto profile, StudentProfileField field) {
        return switch (field) {
            case EDUCATION_LEVEL -> profile.getEducationLevel();
            case COURSE -> profile.getCourse();
            case CURRENT_YEAR -> profile.getCurrentYear();
            case CURRENT_CLASS -> profile.getCurrentClass();
            case APPLICATION_TYPE -> profile.getApplicationType();
            case REGULAR_MODE -> profile.getRegularMode();
            case HAS_PRIOR_DEGREE -> profile.getCompletedUGDegree();
            case CLASS12_PERCENTILE -> profile.getClass12Percentile();
            case BOARD -> profile.getBoard();
            case PREVIOUS_CLASS_MARKS -> profile.getPreviousClassMarks();
            case ANNUAL_FAMILY_INCOME -> profile.getAnnualFamilyIncome();
            case SOCIAL_CATEGORY -> profile.getSocialCategory();
            case DOMICILE_STATE -> profile.getDomicileState();
            case INSTITUTION_NAME -> profile.getInstitutionName();
            case INSTITUTION_TYPE -> profile.getInstitutionType();
            case RECEIVING_OTHER_SCHOLARSHIP -> profile.getReceivingOtherScholarship();
            case HAS_DISABILITY -> profile.getHasDisability();
            case DISABILITY_PERCENTAGE -> profile.getDisabilityPercentage();
            case NATIONALITY -> profile.getNationality();
            // Scheme-only fields without a profile column yet, or any field
            // the profile does not store, are treated as missing.
            case COURSE_TYPE, STUDY_LEVEL, ADMISSION_RANK,
                    HAS_VALID_DISABILITY_CERTIFICATE, HAS_UDID_OR_UDID_ENROLLMENT,
                    SIBLINGS_RECEIVING_BENEFIT, GENDER, DATE_OF_BIRTH, STREAM,
                    CLASS12_PASSED_YEAR, DISABILITY_TYPE -> null;
        };
    }

    private boolean isPresentValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }
}