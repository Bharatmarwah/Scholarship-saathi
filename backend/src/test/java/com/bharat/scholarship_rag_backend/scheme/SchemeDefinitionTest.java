package com.bharat.scholarship_rag_backend.scheme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemeDefinitionTest {

    private final SchemeRegistry registry = new SchemeRegistry();

    @Test
    void exactlyFiveSchemesAreConfigured() {
        assertEquals(5, registry.allSchemes().size());
        assertEquals(Set.of(
                        SchemeType.PM_USP_CSSS,
                        SchemeType.ISHAN_UDAY,
                        SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS,
                        SchemeType.TOP_CLASS_SC,
                        SchemeType.TOP_CLASS_PWD),
                Set.copyOf(registry.allSchemes()));
    }

    @Test
    void resolveAllReturnsEveryNamedSchemeInRegistryOrder() {
        assertEquals(
                List.of(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, SchemeType.TOP_CLASS_SC),
                registry.resolveAll("YASASVI and top class SC"));
    }

    @Test
    void resolveAllAcceptsCommaSeparatedAndAmpersandSeparated() {
        assertEquals(
                List.of(SchemeType.ISHAN_UDAY, SchemeType.TOP_CLASS_PWD),
                registry.resolveAll("ishan uday, top class pwd"));
        assertEquals(
                List.of(SchemeType.ISHAN_UDAY, SchemeType.TOP_CLASS_PWD),
                registry.resolveAll("ishan uday & top class pwd"));
    }

    @Test
    void resolveAllDoesNotInventSchemesFromPartialWords() {
        // "disability" on its own is not an alias, and "top class" is only ever
        // a full alias, so no scheme may be fabricated from the fragments.
        assertEquals(
                List.of(SchemeType.ISHAN_UDAY),
                registry.resolveAll("ishan & disability top class"));
    }

    @Test
    void resolveAllAcceptsBarePwdAbbreviation() {
        assertEquals(
                List.of(SchemeType.TOP_CLASS_PWD),
                registry.resolveAll("PWD"));
        assertEquals(
                List.of(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, SchemeType.TOP_CLASS_PWD),
                registry.resolveAll("YASASVI and PWD"));
    }

    @Test
    void resolveAllCollapsesDuplicates() {
        assertEquals(
                List.of(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, SchemeType.TOP_CLASS_SC),
                registry.resolveAll("YASASVI, top class sc, YASASVI"));
    }

    @Test
    void repeatedSingleNameResolvesOnce() {
        assertEquals(
                List.of(SchemeType.TOP_CLASS_SC),
                registry.resolveAll("top class sc, top class sc"));
    }

    @Test
    void longestAliasWinsOverShorterAliasItContains() {
        assertEquals(
                List.of(SchemeType.TOP_CLASS_SC),
                registry.resolveAll("top class sc for students of sc"));
        assertEquals(
                List.of(SchemeType.PM_USP_CSSS),
                registry.resolveAll("PM-USP CSSS post matric"));
    }

    @Test
    void resolveAllDropsUnknownPartsWithoutGuessing() {
        assertEquals(
                List.of(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                registry.resolveAll("YASASVI and Care for U"));
        assertEquals(List.of(), registry.resolveAll("Care for U, PG Indira, E-spark"));
    }

    @Test
    void resolveAllIgnoresSurroundingText() {
        assertEquals(List.of(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                registry.resolveAll("top class schools"));
    }

    @Test
    void resolveAllHandlesNullBlankAndNoScheme() {
        assertEquals(List.of(), registry.resolveAll(null));
        assertEquals(List.of(), registry.resolveAll("   "));
        assertEquals(List.of(), registry.resolveAll("what is the income limit"));
    }

    @Test
    void resolveAllReturnsEverySchemeForAllFiveNames() {
        assertEquals(
                registry.allSchemes(),
                registry.resolveAll("PM-USP CSSS, Ishan Uday, YASASVI top class schools, top class SC, top class PWD"));
    }

    @Test
    void resolveRejectsReferencesNamingMoreThanOneScheme() {
        assertEquals(Optional.empty(), registry.resolve("YASASVI and Ishan Uday"));
        assertEquals(Optional.of(SchemeType.TOP_CLASS_SC), registry.resolve("top class SC"));
    }

    @Test
    void resolveIsCaseAndPunctuationInsensitive() {
        assertEquals(Optional.of(SchemeType.PM_USP_CSSS), registry.resolve("PM-USP CSSS!"));
        assertEquals(Optional.of(SchemeType.TOP_CLASS_PWD), registry.resolve("  Top_Class_PWD  "));
    }

    @Test
    void fullOfficialSchemeNamesResolveToTheirOwnScheme() {
        assertEquals(Optional.of(SchemeType.TOP_CLASS_PWD),
                registry.resolve("Top Class Education for Students with Disabilities"));
        assertEquals(Optional.of(SchemeType.TOP_CLASS_SC),
                registry.resolve("Top Class Education for SC Students"));
    }

    @Test
    void aPwdQuestionIsNeverResolvedToTheScScheme() {
        assertEquals(List.of(SchemeType.TOP_CLASS_PWD),
                registry.resolveAll("Am I eligible for Top Class Education for Students with Disabilities?"));
        assertEquals(List.of(SchemeType.TOP_CLASS_SC),
                registry.resolveAll("Am I eligible for Top Class Education for SC Students?"));
    }

    @Test
    void everyAliasResolvesBackToItsScheme() {
        for (String alias : registry.normalizedAliasKeys()) {
            assertTrue(registry.resolve(alias).isPresent(),
                    "alias must resolve: " + alias);
        }
    }

    @Test
    void everyCatalogueDisplayNameResolvesToExactlyItsOwnScheme() {
        SchemeMetadataCatalog metadataCatalog = new SchemeMetadataCatalog();

        for (SchemeType type : registry.allSchemes()) {
            String displayName = metadataCatalog.forScheme(type).getSchemeName();
            assertEquals(Optional.of(type), registry.resolve(displayName),
                    "catalogue display name must resolve: " + displayName);
            assertEquals(List.of(type), registry.resolveAll(displayName),
                    "catalogue display name must not bleed into another scheme: " + displayName);
        }
    }

    @Test
    void singularTopClassSchoolDisplayNameIsYasasviNotTopClassSc() {
        assertEquals(List.of(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                registry.resolveAll("PM YASASVI Top Class School"));
        assertEquals(List.of(SchemeType.TOP_CLASS_SC),
                registry.resolveAll("Top Class Education - SC"));
    }

    @Test
    void pmUspDiscoveryFields() {
        SchemeDefinition def = registry.definition(SchemeType.PM_USP_CSSS);
        assertEquals(Set.of(
                        StudentProfileField.EDUCATION_LEVEL,
                        StudentProfileField.COURSE,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.ANNUAL_FAMILY_INCOME),
                def.getDiscoveryFields());
    }

    @Test
    void pmUspEssentialFields() {
        SchemeDefinition def = registry.definition(SchemeType.PM_USP_CSSS);
        assertEquals(Set.of(
                        StudentProfileField.COURSE,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.CLASS12_PERCENTILE,
                        StudentProfileField.BOARD,
                        StudentProfileField.ANNUAL_FAMILY_INCOME,
                        StudentProfileField.REGULAR_MODE,
                        StudentProfileField.INSTITUTION_NAME),
                def.getEssentialFields());
    }

    @Test
    void pmUspConditionalFields() {
        SchemeDefinition def = registry.definition(SchemeType.PM_USP_CSSS);
        assertEquals(Set.of(
                        StudentProfileField.APPLICATION_TYPE,
                        StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                def.getConditionalFields());
    }

    @Test
    void ishanUdayDiscoveryAndEssential() {
        SchemeDefinition def = registry.definition(SchemeType.ISHAN_UDAY);
        assertEquals(Set.of(
                        StudentProfileField.EDUCATION_LEVEL,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.DOMICILE_STATE,
                        StudentProfileField.ANNUAL_FAMILY_INCOME,
                        StudentProfileField.COURSE),
                def.getDiscoveryFields());
        assertEquals(Set.of(
                        StudentProfileField.EDUCATION_LEVEL,
                        StudentProfileField.COURSE,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.DOMICILE_STATE,
                        StudentProfileField.ANNUAL_FAMILY_INCOME,
                        StudentProfileField.REGULAR_MODE,
                        StudentProfileField.HAS_PRIOR_DEGREE,
                        StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                def.getEssentialFields());
    }

    @Test
    void ishanUdayConditionalFields() {
        SchemeDefinition def = registry.definition(SchemeType.ISHAN_UDAY);
        assertEquals(Set.of(StudentProfileField.COURSE_TYPE), def.getConditionalFields());
    }

    @Test
    void yasasviUsesCurrentClassNotCurrentYear() {
        SchemeDefinition def = registry.definition(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS);
        assertEquals(Set.of(
                        StudentProfileField.CURRENT_CLASS,
                        StudentProfileField.SOCIAL_CATEGORY,
                        StudentProfileField.ANNUAL_FAMILY_INCOME),
                def.getDiscoveryFields());
        assertTrue(def.getEssentialFields().contains(StudentProfileField.CURRENT_CLASS));
        assertFalse(def.getEssentialFields().contains(StudentProfileField.CURRENT_YEAR));
    }

    @Test
    void yasasviConditionalAndSelection() {
        SchemeDefinition def = registry.definition(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS);
        assertEquals(Set.of(
                        StudentProfileField.INSTITUTION_TYPE,
                        StudentProfileField.PREVIOUS_CLASS_MARKS),
                def.getConditionalFields());
        assertEquals(Set.of(StudentProfileField.PREVIOUS_CLASS_MARKS), def.getSelectionFields());
    }

    @Test
    void topClassScChecksSocialCategoryAndInstitution() {
        SchemeDefinition def = registry.definition(SchemeType.TOP_CLASS_SC);
        assertTrue(def.getEssentialFields().contains(StudentProfileField.SOCIAL_CATEGORY));
        assertTrue(def.getEssentialFields().contains(StudentProfileField.INSTITUTION_NAME));
        assertTrue(def.getConditionalFields().contains(StudentProfileField.SIBLINGS_RECEIVING_BENEFIT));
        assertEquals(Set.of(StudentProfileField.ADMISSION_RANK), def.getSelectionFields());
    }

    @Test
    void topClassPwdChecksDisabilityFields() {
        SchemeDefinition def = registry.definition(SchemeType.TOP_CLASS_PWD);
        assertTrue(def.getEssentialFields().contains(StudentProfileField.HAS_DISABILITY));
        assertTrue(def.getEssentialFields().contains(StudentProfileField.DISABILITY_PERCENTAGE));
        assertTrue(def.getEssentialFields().contains(StudentProfileField.NATIONALITY));
        assertTrue(def.getConditionalFields().contains(StudentProfileField.HAS_VALID_DISABILITY_CERTIFICATE));
        assertTrue(def.getConditionalFields().contains(StudentProfileField.HAS_UDID_OR_UDID_ENROLLMENT));
    }

    @Test
    void noThreshholdEncodingInAnyDefinition() {
        // Thresholds like socialCategory == SC, income <= 450000, class in 9-12
        // do not exist. Definitions only carry required information fields.
        for (SchemeType type : registry.allSchemes()) {
            SchemeDefinition def = registry.definition(type);
            assertFalse(def.getEssentialFields().isEmpty());
            // Definitions must not contain numeric threshold text anywhere.
            String dump = def.toString();
            assertFalse(dump.contains(">="));
            assertFalse(dump.contains("<="));
            assertFalse(dump.contains("450000"));
            assertFalse(dump.contains("800000"));
        }
    }

    @Test
    void resolveKnownSchemeAliases() {
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("PM-USP").orElseThrow());
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("pm usp").orElseThrow());
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("CSSS").orElseThrow());
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("PM CSS USP").orElseThrow());
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("pm css usp").orElseThrow());
        assertEquals(SchemeType.ISHAN_UDAY, registry.resolve("Ishan Uday").orElseThrow());
        assertEquals(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, registry.resolve("PM YASASVI").orElseThrow());
        assertEquals(SchemeType.TOP_CLASS_SC, registry.resolve("Top Class SC").orElseThrow());
        assertEquals(SchemeType.TOP_CLASS_PWD, registry.resolve("Top Class PWD").orElseThrow());
    }

    @Test
    void unsupportedSchemeIsNotSilentlyMapped() {
        assertTrue(registry.resolve("Some Random Scholarship").isEmpty());
        assertTrue(registry.resolve("PM-USP and Ishan Uday both").isEmpty());
        assertTrue(registry.resolve("").isEmpty());
        assertTrue(registry.resolve(null).isEmpty());
    }

    @Test
    void unknownSchemeThrowsOnDefinitionAccess() {
        assertThrows(IllegalArgumentException.class, () -> registry.definition(null));
    }

    @Test
    void discoveryOrderCoversEverySchemesDiscoveryFields() {
        Set<StudentProfileField> order = Set.copyOf(registry.discoveryOrder());
        for (SchemeType type : registry.allSchemes()) {
            SchemeDefinition def = registry.definition(type);
            assertTrue(order.containsAll(def.getDiscoveryFields()),
                    def.getScheme() + " discovery fields must be covered by the discovery order");
        }
    }

    @Test
    void discoveryOrderIsStableAndConversational() {
        assertEquals(StudentProfileField.EDUCATION_LEVEL, registry.discoveryOrder().get(0));
        // Deterministic across calls
        assertEquals(registry.discoveryOrder(), registry.discoveryOrder());
    }
}