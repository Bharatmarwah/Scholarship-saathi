package com.bharat.scholarship_rag_backend.entity;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One row per scholarship scheme: the administrative and contact facts that
 * describe the scheme itself, independent of any single paragraph of its text.
 *
 * <p>The primary key is the {@link SchemeType} the rest of the application
 * already uses, so a chunk cannot be attached to a scheme the validator does
 * not know about. Values here are transcribed from the official guideline and
 * FAQ documents in {@code cleaned-markdown}; nothing is inferred.
 *
 * <p>Numeric eligibility thresholds (income ceilings, percentile cut-offs,
 * disability percentages) deliberately do NOT live here. They change
 * independently of the prose and belong in a separate structured facts table
 * read by the eligibility engine. Keeping them apart means an amended circular
 * never requires re-embedding the narrative chunks.
 */
@Entity
@Table(name = "scholarship_metadata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScholarshipMetadata {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 40)
    private SchemeType scheme;

    @Column(name = "scheme_name", nullable = false)
    private String schemeName;

    @Column(name = "short_name", length = 40)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme_category", nullable = false, length = 40)
    private SchemeCategory schemeCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_level", nullable = false, length = 40)
    private EducationLevel educationLevel;

    // Administration

    @Column(name = "administering_ministry")
    private String administeringMinistry;

    @Column(name = "administering_department")
    private String administeringDepartment;

    @Column(name = "administering_authority")
    private String administeringAuthority;

    @Column(name = "contact_address")
    private String contactAddress;

    // Portals and contacts

    @Column(name = "official_website")
    private String officialWebsite;

    @Column(name = "application_portal_url")
    private String applicationPortalUrl;

    @Column(name = "grievance_url")
    private String grievanceUrl;

    @Column(name = "payment_tracking_url")
    private String paymentTrackingUrl;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 60)
    private String contactPhone;

    // Validity window

    @Column(name = "academic_year_from", length = 20)
    private String academicYearFrom;

    @Column(name = "academic_year_to", length = 20)
    private String academicYearTo;

    @Column(name = "guideline_effective_date")
    private LocalDate guidelineEffectiveDate;

    @Column(name = "verification_deadline_note")
    private String verificationDeadlineNote;

    // Application cycle

    @Column(name = "accepts_fresh_applications")
    private Boolean acceptsFreshApplications;

    @Column(name = "accepts_renewal_applications")
    private Boolean acceptsRenewalApplications;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_mode", length = 40)
    private ApplicationMode applicationMode;

    @Column(name = "selection_method")
    private String selectionMethod;

    // Source provenance

    @Column(name = "source_file_number")
    private String sourceFileNumber;

    @Column(name = "source_documents")
    private String sourceDocuments;
}