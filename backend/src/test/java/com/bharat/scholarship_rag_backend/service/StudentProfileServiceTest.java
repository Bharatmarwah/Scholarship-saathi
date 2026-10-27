package com.bharat.scholarship_rag_backend.service;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.entity.StudentProfile;
import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.repository.StudentProfileRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentProfileServiceTest {

    private final StudentProfileRepo repo = mock(StudentProfileRepo.class);

    private StudentProfileService serviceForNewProfile(String conversationId) {
        StudentProfile entity = new StudentProfile();
        entity.setConversationId(conversationId);
        when(repo.findByConversationId(conversationId))
                .thenReturn(Optional.empty(), Optional.of(entity));
        when(repo.insertIfAbsent(any(UUID.class), eq(conversationId), any(LocalDateTime.class)))
                .thenReturn(1);
        when(repo.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new StudentProfileService(repo, new ProfileValueNormalizer());
    }

    @Test
    void storesValuesTheLlmAlreadyNormalized() {
        String conversationId = "conv-normalized";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId, Map.of(
                "CURRENT_YEAR", 3,
                "CLASS12_PERCENTILE", 92.0,
                "ANNUAL_FAMILY_INCOME", 500000,
                "COURSE", "B.Tech"));

        assertEquals(3, dto.getCurrentYear());
        assertEquals(92.0, dto.getClass12Percentile());
        assertEquals(0, new BigDecimal("500000").compareTo(dto.getAnnualFamilyIncome()));
        assertEquals("B.Tech", dto.getCourse());
    }

    @Test
    void rejectsUnnormalizedWordValuesAndLeavesFieldUnset() {
        String conversationId = "conv-messy";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId, Map.of(
                "CURRENT_YEAR", "3rd",
                "ANNUAL_FAMILY_INCOME", "5 lakh"));

        assertNull(dto.getCurrentYear());
        assertNull(dto.getAnnualFamilyIncome());
    }

    @Test
    void rejectsImplausibleNumbers() {
        String conversationId = "conv-implausible";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId, Map.of(
                "CURRENT_YEAR", 99,
                "ANNUAL_FAMILY_INCOME", -1));

        assertNull(dto.getCurrentYear());
        assertNull(dto.getAnnualFamilyIncome());
    }

    @Test
    void doesNotOverwriteAnAlreadyStoredValue() {
        String conversationId = "conv-existing";
        StudentProfile entity = new StudentProfile();
        entity.setConversationId(conversationId);
        entity.setCurrentYear(2);
        when(repo.findByConversationId(conversationId)).thenReturn(Optional.of(entity));
        StudentProfileService service = new StudentProfileService(repo, new ProfileValueNormalizer());

        StudentProfileDto dto = service.applyExtracted(conversationId, Map.of("CURRENT_YEAR", 2));

        assertEquals(2, dto.getCurrentYear());
        verify(repo, never()).save(any(StudentProfile.class));
        verify(repo, never()).insertIfAbsent(any(UUID.class), anyString(), any(LocalDateTime.class));
    }

    @Test
    void acceptsZeroIncomeAsPresent() {
        String conversationId = "conv-zero-income";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId, Map.of("ANNUAL_FAMILY_INCOME", 0));

        assertEquals(0, new BigDecimal("0").compareTo(dto.getAnnualFamilyIncome()));
    }

    @Test
    void storesCanonicalApplicationType() {
        String conversationId = "conv-application-type";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId,
                Map.of("APPLICATION_TYPE", "renewal"));

        assertEquals("RENEWAL", dto.getApplicationType());
    }

    @Test
    void canonicalizesSocialCategoryVariants() {
        String conversationId = "conv-category";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId,
                Map.of("SOCIAL_CATEGORY", "Other Backward Class (OBC-NCL)"));

        assertEquals("OBC", dto.getSocialCategory());
    }

    @Test
    void keepsAnUnrecognizedCategoryInsteadOfRejectingIt() {
        String conversationId = "conv-category-unknown";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId,
                Map.of("SOCIAL_CATEGORY", "creamy layer"));

        assertEquals("creamy layer", dto.getSocialCategory());
    }

    @Test
    void rejectsBlankTextValuesInsteadOfStoringEmptyStrings() {
        String conversationId = "conv-blank";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId, Map.of(
                "COURSE", "   ",
                "SOCIAL_CATEGORY", ""));

        assertNull(dto.getCourse());
        assertNull(dto.getSocialCategory());
    }

    @Test
    void canonicalizesCourseSpellings() {
        String conversationId = "conv-course";
        StudentProfileService service = serviceForNewProfile(conversationId);

        StudentProfileDto dto = service.applyExtracted(conversationId,
                Map.of("COURSE", "btech"));

        assertEquals("B.Tech", dto.getCourse());
    }
}
