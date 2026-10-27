package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An informational question needs the schemes it is about, but it must not
 * start a profile-collection flow. {@code resolveScope} is the read-only
 * alternative to {@code validateAll} that the orchestrator uses for those
 * questions.
 */
class ValidationEngineScopeTest {

    private final SchemeRegistry registry = new SchemeRegistry();
    private final PendingValidationState pending = new PendingValidationState();
    private final ValidationEngine engine = new ValidationEngine(
            registry,
            new ProfileFieldAccess(),
            new QuestionBuilder(),
            pending);

    @Test
    void oneNamedSchemeBecomesASpecificScope() {
        ValidationResult result = engine.resolveScope(List.of("Top Class SC"), "c1");

        assertEquals(ValidationStatus.READY, result.getStatus());
        assertEquals(ValidationMode.SPECIFIC_SCHEME, result.getMode());
        assertEquals(SchemeType.TOP_CLASS_SC, result.getTargetScheme());
        assertEquals(List.of(SchemeType.TOP_CLASS_SC), result.getTargetSchemes());
        assertTrue(result.getMissingFields().isEmpty());
    }

    @Test
    void severalNamedSchemesBecomeAMultiSchemeScope() {
        ValidationResult result = engine.resolveScope(
                List.of("Top Class SC", "Ishan Uday"), "c1");

        assertEquals(ValidationStatus.READY, result.getStatus());
        assertEquals(ValidationMode.MULTI_SCHEME, result.getMode());
        assertNull(result.getTargetScheme());
        assertEquals(List.of(SchemeType.TOP_CLASS_SC, SchemeType.ISHAN_UDAY),
                result.getTargetSchemes());
    }

    @Test
    void noNamedSchemeFallsBackToEveryScheme() {
        ValidationResult result = engine.resolveScope(List.of(), "c1");

        assertEquals(ValidationStatus.READY, result.getStatus());
        assertEquals(ValidationMode.SCHOLARSHIP_DISCOVERY, result.getMode());
        assertEquals(registry.allSchemes(), result.getTargetSchemes());
    }

    @Test
    void aPendingSpecificSchemeScopesAFollowUpFactualQuestion() {
        pending.store("c1", SchemeType.TOP_CLASS_SC,
                List.of(StudentProfileField.EDUCATION_LEVEL));

        ValidationResult result = engine.resolveScope(List.of(), "c1");

        assertEquals(ValidationMode.SPECIFIC_SCHEME, result.getMode());
        assertEquals(SchemeType.TOP_CLASS_SC, result.getTargetScheme());
    }

    @Test
    void resolvingScopeNeverWritesOrClearsPendingState() {
        pending.store("c1", SchemeType.ISHAN_UDAY,
                List.of(StudentProfileField.COURSE));

        engine.resolveScope(List.of("Top Class SC"), "c1");

        assertTrue(pending.hasPending("c1"));
        assertEquals(List.of(SchemeType.ISHAN_UDAY), pending.targetSchemes("c1"));
        assertEquals(List.of(StudentProfileField.COURSE), pending.askedFields("c1"));
    }

    @Test
    void schemesNamedInResolvesOnlyWhatTheCurrentQueryActuallyNames() {
        assertEquals(List.of(SchemeType.TOP_CLASS_SC),
                engine.schemesNamedIn("what are the administrative expenses for top class sc?"));
        assertEquals(List.of(SchemeType.ISHAN_UDAY, SchemeType.TOP_CLASS_SC),
                engine.schemesNamedIn("compare top class sc and ishan uday"));
        assertEquals(List.of(), engine.schemesNamedIn("Where are Scheme Guidelines available?"));
        assertEquals(List.of(), engine.schemesNamedIn(null));
    }

    @Test
    void aDiscoveryOnlyPendingEntryDoesNotCountAsASchemeScope() {
        pending.store("c1", List.of(), List.of(StudentProfileField.EDUCATION_LEVEL));

        ValidationResult result = engine.resolveScope(List.of(), "c1");

        assertEquals(ValidationMode.SCHOLARSHIP_DISCOVERY, result.getMode());
        assertEquals(registry.allSchemes(), result.getTargetSchemes());
        assertFalse(pending.hasPending("c1"),
                "discovery-only pending state is not a scheme continuation");
        assertEquals(List.of(StudentProfileField.EDUCATION_LEVEL),
                pending.askedFields("c1"));
    }
}
