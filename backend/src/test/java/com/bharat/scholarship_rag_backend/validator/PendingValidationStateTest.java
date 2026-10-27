package com.bharat.scholarship_rag_backend.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.junit.jupiter.api.Test;

class PendingValidationStateTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void expiresAPendingValidationAfterItsTtl() {
        MutableClock clock = new MutableClock(START);
        PendingValidationState state =
                new PendingValidationState(Duration.ofMinutes(30), clock);

        state.store("c1", SchemeType.TOP_CLASS_SC,
                List.of(StudentProfileField.EDUCATION_LEVEL));

        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        assertTrue(state.hasPending("c1"));
        assertTrue(state.hasAskedFields("c1"));

        clock.advance(Duration.ofSeconds(1));
        assertFalse(state.hasPending("c1"));
        assertFalse(state.hasAskedFields("c1"));
        assertEquals(List.of(), state.targetSchemes("c1"));
        assertEquals(List.of(), state.askedFields("c1"));
    }

    @Test
    void expiresADiscoveryEntryWhoseAskedFieldsWereTheOnlyState() {
        MutableClock clock = new MutableClock(START);
        PendingValidationState state =
                new PendingValidationState(Duration.ofMinutes(1), clock);

        state.store("c1", List.of(), List.of(StudentProfileField.CURRENT_CLASS));
        assertTrue(state.hasAskedFields("c1"));

        clock.advance(Duration.ofMinutes(1));
        assertFalse(state.hasAskedFields("c1"));
    }

    @Test
    void aFreshStoreRestartsTheTtl() {
        MutableClock clock = new MutableClock(START);
        PendingValidationState state =
                new PendingValidationState(Duration.ofMinutes(10), clock);

        state.store("c1", SchemeType.ISHAN_UDAY,
                List.of(StudentProfileField.EDUCATION_LEVEL));
        clock.advance(Duration.ofMinutes(9));
        state.store("c1", SchemeType.ISHAN_UDAY,
                List.of(StudentProfileField.SOCIAL_CATEGORY));
        clock.advance(Duration.ofMinutes(9));

        assertTrue(state.hasPending("c1"));
        assertEquals(List.of(StudentProfileField.SOCIAL_CATEGORY),
                state.askedFields("c1"));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
