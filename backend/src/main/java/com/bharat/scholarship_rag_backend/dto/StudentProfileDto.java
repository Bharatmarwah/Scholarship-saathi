package com.bharat.scholarship_rag_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDto {

    private String conversationId;

    // Education
    private String educationLevel;

    private String course;

    private Integer currentYear;

    private Integer currentClass;

    private String applicationType;

    private Boolean regularMode;

    private Boolean completedUGDegree;

    private Double class12Percentile;

    private String board;

    private Double previousClassMarks;

    // Financial
    private BigDecimal annualFamilyIncome;

    // Social
    private String socialCategory;

    // Domicile
    private String domicileState;

    // Institution
    private String institutionName;

    private String institutionType;

    // Existing scholarship
    private Boolean receivingOtherScholarship;

    // Disability
    private Boolean hasDisability;

    private Integer disabilityPercentage;

    private String nationality;
}