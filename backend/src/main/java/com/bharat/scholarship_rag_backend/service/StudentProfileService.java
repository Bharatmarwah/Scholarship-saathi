package com.bharat.scholarship_rag_backend.service;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.entity.StudentProfile;
import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.repository.StudentProfileRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
@Service
public class StudentProfileService {

    private static final BigDecimal MAX_ANNUAL_INCOME = BigDecimal.valueOf(100_000_000L);

    private static final IntPredicate YEAR_PLAUSIBLE = value -> value >= 1 && value <= 8;
    private static final IntPredicate CLASS_PLAUSIBLE = value -> value >= 1 && value <= 12;
    private static final IntPredicate PERCENT_PLAUSIBLE = value -> value >= 1 && value <= 100;
    private static final DoublePredicate MARKS_PLAUSIBLE = value -> value >= 1 && value <= 100;
    private static final Predicate<BigDecimal> INCOME_PLAUSIBLE = value ->
            value.signum() >= 0 && value.compareTo(MAX_ANNUAL_INCOME) <= 0;

    /**
     * Whether an extracted field changes its value, stays the same, or is
     * rejected. Rejected values are logged so a wrong extraction is visible
     * instead of silently looping.
     */
    private enum ApplyOutcome {
        APPLIED,
        UNCHANGED,
        REJECTED
    }

    private final StudentProfileRepo studentProfileRepo;
    private final ProfileValueNormalizer profileValueNormalizer;

    public StudentProfileService(
            StudentProfileRepo studentProfileRepo,
            ProfileValueNormalizer profileValueNormalizer) {
        this.studentProfileRepo = studentProfileRepo;
        this.profileValueNormalizer = profileValueNormalizer;
    }

    @Transactional
    public StudentProfileDto getOrCreate(String conversationId) {
        upsertProfileRow(conversationId);
        StudentProfile profile = studentProfileRepo.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Student profile must exist after upsert for conversation " + conversationId));
        return toDto(profile);
    }

    /**
     * Applies the values extracted by the query composer onto the student
     * profile. A field is written only when the profile has no value yet or
     * the extracted value corrects the stored one. Fields that do not map to
     * the profile entity are logged and skipped.
     */
    @Transactional
    public StudentProfileDto applyExtracted(String conversationId, Map<String, Object> extractedValues) {
        if (extractedValues == null || extractedValues.isEmpty()) {
            return getOrCreate(conversationId);
        }

        StudentProfile profile = studentProfileRepo.findByConversationId(conversationId).orElse(null);
        boolean isNew = profile == null;

        if (isNew) {
            upsertProfileRow(conversationId);
            profile = studentProfileRepo.findByConversationId(conversationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Student profile must exist after upsert for conversation " + conversationId));
        }

        boolean changed = false;
        for (Map.Entry<String, Object> entry : extractedValues.entrySet()) {
            ApplyOutcome outcome = applyValue(profile, entry.getKey(), entry.getValue());
            if (outcome == ApplyOutcome.APPLIED) {
                changed = true;
            } else if (outcome == ApplyOutcome.REJECTED) {
                log.warn("Extracted value for '{}' was rejected and left unset (raw='{}')",
                        entry.getKey(), safeForLog(entry.getValue()));
            }
        }

        if (isNew || changed) {
            profile.setUpdatedAt(LocalDateTime.now());
            studentProfileRepo.save(profile);
        }

        return toDto(profile);
    }

    private String safeForLog(Object raw) {
        if (raw instanceof CharSequence text) {
            String trimmed = text.toString();
            return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "...";
        }
        return String.valueOf(raw);
    }

    /**
     * Creates the profile row atomically when it does not exist yet, using a
     * database-level {@code INSERT ... ON CONFLICT DO NOTHING}. Concurrent
     * requests for the same conversation share one row, and the UNIQUE
     * constraint on {@code conversation_id} is preserved.
     */
    private void upsertProfileRow(String conversationId) {
        studentProfileRepo.insertIfAbsent(
                UUID.randomUUID(),
                conversationId,
                LocalDateTime.now());
    }

    private ApplyOutcome applyValue(StudentProfile profile, String fieldName, Object raw) {
        if (fieldName == null || raw == null) {
            return ApplyOutcome.UNCHANGED;
        }
        return switch (fieldName) {
            case "EDUCATION_LEVEL" ->
                    applyNormalizedString(raw, profileValueNormalizer::normalizeEducationLevel,
                            profile::getEducationLevel, profile::setEducationLevel);
            case "COURSE" ->
                    applyNormalizedString(raw, profileValueNormalizer::normalizeCourse,
                            profile::getCourse, profile::setCourse);
            case "CURRENT_YEAR" ->
                    applyInteger(raw, YEAR_PLAUSIBLE, profile::getCurrentYear, profile::setCurrentYear);
            case "CURRENT_CLASS" ->
                    applyInteger(raw, CLASS_PLAUSIBLE, profile::getCurrentClass, profile::setCurrentClass);
            case "APPLICATION_TYPE" ->
                    applyNormalizedString(raw, profileValueNormalizer::normalizeApplicationType,
                            profile::getApplicationType, profile::setApplicationType);
            case "REGULAR_MODE" -> applyBoolean(raw, profile::getRegularMode, profile::setRegularMode);
            case "HAS_PRIOR_DEGREE" ->
                    applyBoolean(raw, profile::getCompletedUGDegree, profile::setCompletedUGDegree);
            case "CLASS12_PERCENTILE" ->
                    applyDouble(raw, MARKS_PLAUSIBLE, profile::getClass12Percentile,
                            profile::setClass12Percentile);
            case "BOARD" -> applyString(raw, profile::getBoard, profile::setBoard);
            case "PREVIOUS_CLASS_MARKS" ->
                    applyDouble(raw, MARKS_PLAUSIBLE, profile::getPreviousClassMarks,
                            profile::setPreviousClassMarks);
            case "ANNUAL_FAMILY_INCOME" ->
                    applyBigDecimal(raw, INCOME_PLAUSIBLE, profile::getAnnualFamilyIncome,
                            profile::setAnnualFamilyIncome);
            case "SOCIAL_CATEGORY" ->
                    applyNormalizedString(raw, profileValueNormalizer::normalizeSocialCategory,
                            profile::getSocialCategory, profile::setSocialCategory);
            case "DOMICILE_STATE" -> applyString(raw, profile::getDomicileState, profile::setDomicileState);
            case "INSTITUTION_NAME" -> applyString(raw, profile::getInstitutionName, profile::setInstitutionName);
            case "INSTITUTION_TYPE" -> applyString(raw, profile::getInstitutionType, profile::setInstitutionType);
            case "RECEIVING_OTHER_SCHOLARSHIP" ->
                    applyBoolean(raw, profile::getReceivingOtherScholarship,
                            profile::setReceivingOtherScholarship);
            case "HAS_DISABILITY" -> applyBoolean(raw, profile::getHasDisability, profile::setHasDisability);
            case "DISABILITY_PERCENTAGE" ->
                    applyInteger(raw, PERCENT_PLAUSIBLE, profile::getDisabilityPercentage,
                            profile::setDisabilityPercentage);
            case "NATIONALITY" -> applyString(raw, profile::getNationality, profile::setNationality);
            case "COURSE_TYPE", "STUDY_LEVEL", "ADMISSION_RANK",
                    "HAS_VALID_DISABILITY_CERTIFICATE", "HAS_UDID_OR_UDID_ENROLLMENT",
                    "SIBLINGS_RECEIVING_BENEFIT", "GENDER", "DATE_OF_BIRTH", "STREAM",
                    "CLASS12_PASSED_YEAR", "DISABILITY_TYPE" -> {
                log.debug("Skipping field {} (no profile column yet)", fieldName);
                yield ApplyOutcome.UNCHANGED;
            }
            default -> {
                log.debug("Skipping unknown field {}", fieldName);
                yield ApplyOutcome.UNCHANGED;
            }
        };
    }

    private ApplyOutcome applyString(Object raw, Supplier<String> getter, Consumer<String> setter) {
        String value = toText(raw);
        if (value == null || value.isBlank()) {
            return ApplyOutcome.REJECTED;
        }
        return setIfChanged(getter.get(), value, setter)
                ? ApplyOutcome.APPLIED
                : ApplyOutcome.UNCHANGED;
    }

    /**
     * Canonicalizes the text through the rule-based normalizer before storing.
     * If normalization returns null or blank the cleaned text is stored anyway,
     * so an unrecognized value is never rejected and never loops.
     */
    private ApplyOutcome applyNormalizedString(
            Object raw,
            Function<String, String> normalize,
            Supplier<String> getter,
            Consumer<String> setter) {

        String cleaned = toText(raw);
        if (cleaned == null || cleaned.isBlank()) {
            return ApplyOutcome.REJECTED;
        }
        String value = normalize.apply(cleaned);
        if (value == null || value.isBlank()) {
            value = cleaned;
        }
        return setIfChanged(getter.get(), value, setter)
                ? ApplyOutcome.APPLIED
                : ApplyOutcome.UNCHANGED;
    }

    private ApplyOutcome applyInteger(
            Object raw,
            IntPredicate plausible,
            Supplier<Integer> getter,
            Consumer<Integer> setter) {

        Integer value = toInteger(raw);
        if (value == null || !plausible.test(value)) {
            return ApplyOutcome.REJECTED;
        }
        return setIfChanged(getter.get(), value, setter)
                ? ApplyOutcome.APPLIED
                : ApplyOutcome.UNCHANGED;
    }

    private ApplyOutcome applyBoolean(Object raw, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        Boolean value = toBoolean(raw);
        if (value == null) {
            return ApplyOutcome.REJECTED;
        }
        return setIfChanged(getter.get(), value, setter)
                ? ApplyOutcome.APPLIED
                : ApplyOutcome.UNCHANGED;
    }

    private ApplyOutcome applyDouble(
            Object raw,
            DoublePredicate plausible,
            Supplier<Double> getter,
            Consumer<Double> setter) {

        Double value = toDouble(raw);
        if (value == null || !plausible.test(value)) {
            return ApplyOutcome.REJECTED;
        }
        return setIfChanged(getter.get(), value, setter)
                ? ApplyOutcome.APPLIED
                : ApplyOutcome.UNCHANGED;
    }

    private ApplyOutcome applyBigDecimal(
            Object raw,
            Predicate<BigDecimal> plausible,
            Supplier<BigDecimal> getter,
            Consumer<BigDecimal> setter) {

        BigDecimal value = toBigDecimal(raw);
        if (value == null || !plausible.test(value)) {
            return ApplyOutcome.REJECTED;
        }
        return setIfChanged(getter.get(), value, setter)
                ? ApplyOutcome.APPLIED
                : ApplyOutcome.UNCHANGED;
    }

    private <T> boolean setIfChanged(T current, T value, Consumer<T> setter) {
        if (value == null) {
            return false;
        }
        if (current == null || !isSameValue(current, value)) {
            setter.accept(value);
            return true;
        }
        return false;
    }

    /**
     * {@code BigDecimal.equals} is scale-sensitive ({@code 3.0} and
     * {@code 3.00} are not equal), so the same income stated as "3 lakh" and
     * "3.00 lakh" must still count as unchanged.
     */
    private static boolean isSameValue(Object current, Object value) {
        if (current instanceof BigDecimal currentAmount && value instanceof BigDecimal newAmount) {
            return currentAmount.compareTo(newAmount) == 0;
        }
        return current.equals(value);
    }

    private String toText(Object raw) {
        if (raw instanceof CharSequence text) {
            return text.toString();
        }
        if (raw instanceof Number number) {
            return number.toString();
        }
        return null;
    }

    private Integer toInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean flag) {
            return flag;
        }
        if (raw instanceof String text) {
            return switch (text.trim().toLowerCase()) {
                case "true", "yes", "1" -> Boolean.TRUE;
                case "false", "no", "0" -> Boolean.FALSE;
                default -> null;
            };
        }
        return null;
    }

    private Double toDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (raw instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private StudentProfileDto toDto(StudentProfile entity) {
        return StudentProfileDto.builder()
                .conversationId(entity.getConversationId())
                .educationLevel(entity.getEducationLevel())
                .course(entity.getCourse())
                .currentYear(entity.getCurrentYear())
                .currentClass(entity.getCurrentClass())
                .applicationType(entity.getApplicationType())
                .regularMode(entity.getRegularMode())
                .completedUGDegree(entity.getCompletedUGDegree())
                .class12Percentile(entity.getClass12Percentile())
                .board(entity.getBoard())
                .previousClassMarks(entity.getPreviousClassMarks())
                .annualFamilyIncome(entity.getAnnualFamilyIncome())
                .socialCategory(entity.getSocialCategory())
                .domicileState(entity.getDomicileState())
                .institutionName(entity.getInstitutionName())
                .institutionType(entity.getInstitutionType())
                .receivingOtherScholarship(entity.getReceivingOtherScholarship())
                .hasDisability(entity.getHasDisability())
                .disabilityPercentage(entity.getDisabilityPercentage())
                .nationality(entity.getNationality())
                .build();
    }
}