package com.bharat.scholarship_rag_backend.scheme;

import com.bharat.scholarship_rag_backend.entity.EducationLevel;
import com.bharat.scholarship_rag_backend.entity.SchemeCategory;
import com.bharat.scholarship_rag_backend.entity.ScholarshipMetadata;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Verified administrative metadata for each supported scheme.
 *
 * <p>Values are transcribed from official Government of India, UGC, and
 * National Scholarship Portal sources. A null means the value could not be
 * verified from an official source; it is never replaced with an inferred
 * contact, date, file number, or scheme-specific URL. Application-cycle flags
 * are snapshots and must be refreshed from the live portal.
 */
@Component
public class SchemeMetadataCatalog {

    private static final String NSP = "https://scholarships.gov.in/";

    private final Map<SchemeType, ScholarshipMetadata> byScheme = new EnumMap<>(SchemeType.class);

    public SchemeMetadataCatalog() {
        add(ScholarshipMetadata.builder()
                .scheme(SchemeType.TOP_CLASS_PWD)
                .schemeName("Top Class Education - PwD")
                .shortName("Top Class PwD")
                .schemeCategory(SchemeCategory.CENTRAL_SECTOR)
                .educationLevel(EducationLevel.HIGHER_EDUCATION)
                .administeringMinistry("Ministry of Social Justice and Empowerment")
                .administeringDepartment("Department of Empowerment of Persons with Disabilities")
                .officialWebsite("https://depwd.gov.in/en/scholarship/")
                .applicationPortalUrl(NSP)
                .academicYearFrom("2026-27")
                .acceptsFreshApplications(true)
                .sourceDocuments("DEPwD: Revised Guidelines of the Central Sector Umbrella Scheme "
                        + "Scholarships for Students with Disabilities; National Scholarship "
                        + "Portal AY 2026-27")
                .build());

        add(ScholarshipMetadata.builder()
                .scheme(SchemeType.ISHAN_UDAY)
                .schemeName("Ishan Uday")
                .shortName("Ishan Uday")
                .schemeCategory(SchemeCategory.CENTRAL_UGC)
                .educationLevel(EducationLevel.HIGHER_EDUCATION)
                .administeringMinistry("Ministry of Education")
                .administeringAuthority("University Grants Commission")
                .contactAddress("University Grants Commission, Bahadur Shah Zafar Marg, "
                        + "New Delhi - 110002")
                .officialWebsite("https://www.ugc.gov.in/Scholarships/stu_Scholarship5")
                .applicationPortalUrl(NSP)
                .contactEmail("contact.ugc@nic.in")
                .contactPhone("011-23604446; 011-23604200")
                .sourceDocuments("UGC: Ishan Uday Special Scholarship Scheme for North Eastern "
                        + "Region; official UGC scheme page")
                .build());

        add(ScholarshipMetadata.builder()
                .scheme(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS)
                .schemeName("PM YASASVI Top Class School")
                .shortName("PM YASASVI School")
                .schemeCategory(SchemeCategory.CENTRAL_SECTOR)
                .educationLevel(EducationLevel.SCHOOL)
                .administeringMinistry("Ministry of Social Justice and Empowerment")
                .administeringAuthority("Department of Social Justice and Empowerment")
                .officialWebsite("https://socialjustice.gov.in/schemes/101")
                .applicationPortalUrl(NSP)
                .academicYearFrom("2026-27")
                .acceptsFreshApplications(true)
                .sourceDocuments("Department of Social Justice and Empowerment: PM-YASASVI "
                        + "scheme page; PM YASASVI Top Class Education in School SOP; "
                        + "National Scholarship Portal AY 2026-27")
                .build());

        add(ScholarshipMetadata.builder()
                .scheme(SchemeType.PM_USP_CSSS)
                .schemeName("PM-USP CSSS")
                .shortName("PM-USP CSSS")
                .schemeCategory(SchemeCategory.CENTRAL_SECTOR)
                .educationLevel(EducationLevel.HIGHER_EDUCATION)
                .administeringMinistry("Ministry of Education")
                .administeringDepartment("Department of Higher Education")
                .administeringAuthority("National Scholarship Division")
                .contactAddress("West Block 1, 2nd Floor, Wing 6, Room No. 6, "
                        + "R. K. Puram, Sector 1, New Delhi 110066")
                .officialWebsite("https://www.education.gov.in/")
                .applicationPortalUrl(NSP)
                .grievanceUrl("https://pgportal.gov.in/")
                .paymentTrackingUrl("https://pfms.nic.in/")
                .contactEmail("ns1-scholarship@gov.in")
                .contactPhone("011-20862360")
                .academicYearFrom("2026-27")
                .verificationDeadlineNote("Renewal application deadline: 31 October 2026; "
                        + "institute verification: 15 November 2026; L2 verification: "
                        + "30 November 2026")
                .acceptsRenewalApplications(true)
                .sourceDocuments("Ministry of Education: PM-USP CSSS Scheme Guidelines; "
                        + "PM-USP CSSS FAQs AY 2025-26; National Scholarship Portal AY 2026-27")
                .build());

        add(ScholarshipMetadata.builder()
                .scheme(SchemeType.TOP_CLASS_SC)
                .schemeName("Top Class Education - SC")
                .shortName("Top Class SC")
                .schemeCategory(SchemeCategory.CENTRAL_SECTOR)
                .educationLevel(EducationLevel.HIGHER_EDUCATION)
                .administeringMinistry("Ministry of Social Justice and Empowerment")
                .administeringAuthority("Department of Social Justice and Empowerment")
                .officialWebsite("https://socialjustice.gov.in/schemes/27")
                .applicationPortalUrl(NSP)
                .academicYearFrom("2026-27")
                .guidelineEffectiveDate(LocalDate.of(2026, 6, 24))
                .verificationDeadlineNote("Student application deadline: 31 October 2026; "
                        + "institute verification: 15 November 2026; L2 verification: "
                        + "30 November 2026")
                .acceptsFreshApplications(true)
                .sourceDocuments("Department of Social Justice and Empowerment: Central Sector "
                        + "Scholarship of Top Class Education for SC Students, Scheme "
                        + "Guidelines 2026-27; National Scholarship Portal AY 2026-27")
                .build());
    }

    private void add(ScholarshipMetadata metadata) {
        byScheme.put(metadata.getScheme(), metadata);
    }

    public ScholarshipMetadata forScheme(SchemeType scheme) {
        ScholarshipMetadata metadata = byScheme.get(scheme);
        if (metadata == null) {
            throw new IllegalArgumentException("No metadata template for scheme " + scheme);
        }
        return metadata;
    }
}
