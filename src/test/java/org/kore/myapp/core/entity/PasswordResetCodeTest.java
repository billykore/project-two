package org.kore.myapp.core.entity;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.valueobject.ContactChannel;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetCodeTest {

    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String HASH = "a".repeat(64);

    private PasswordResetCode issued() {
        return PasswordResetCode.issue(7L, HASH, ContactChannel.EMAIL, NOW, TTL);
    }

    @Test
    void issueSetsTheTimesAndStartsWithNoAttemptsAndNoOutcome() {
        PasswordResetCode code = issued();

        assertThat(code.getUserId()).isEqualTo(7L);
        assertThat(code.getCodeHash()).isEqualTo(HASH);
        assertThat(code.getChannel()).isEqualTo(ContactChannel.EMAIL);
        assertThat(code.getIssuedAt()).isEqualTo(NOW);
        assertThat(code.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(code.getFailedAttempts()).isZero();
        assertThat(code.getUsedAt()).isNull();
        assertThat(code.getInvalidatedAt()).isNull();
    }

    @Test
    void issueRejectsABlankHash() {
        assertThatThrownBy(() -> PasswordResetCode.issue(7L, " ", ContactChannel.EMAIL, NOW, TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordResetCode.issue(7L, null, ContactChannel.EMAIL, NOW, TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCodeIsUsableUntilTheMomentItExpires() {
        PasswordResetCode code = issued();

        assertThat(code.isUsable(NOW)).isTrue();
        assertThat(code.isUsable(NOW.plus(TTL).minusSeconds(1))).isTrue();
        assertThat(code.isUsable(NOW.plus(TTL))).isFalse();
        assertThat(code.isUsable(NOW.plus(TTL).plusSeconds(1))).isFalse();
    }

    @Test
    void markUsedRecordsTheTimeAndEndsUsability() {
        PasswordResetCode code = issued();
        Instant later = NOW.plusSeconds(30);

        code.markUsed(later);

        assertThat(code.getUsedAt()).isEqualTo(later);
        assertThat(code.isUsable(later)).isFalse();
    }

    @Test
    void markUsedOnAnExpiredCodeThrows() {
        PasswordResetCode code = issued();

        assertThatThrownBy(() -> code.markUsed(NOW.plus(TTL)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(code.getUsedAt()).isNull();
    }

    @Test
    void markUsedOnAUsedCodeThrows() {
        PasswordResetCode code = issued();
        code.markUsed(NOW.plusSeconds(1));

        assertThatThrownBy(() -> code.markUsed(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailedAttemptIsCountedAndTheCodeStaysUsableBelowTheMaximum() {
        PasswordResetCode code = issued();

        for (int attempt = 1; attempt <= 4; attempt++) {
            code.recordFailedAttempt(NOW.plusSeconds(attempt), 5);

            assertThat(code.getFailedAttempts()).isEqualTo(attempt);
            assertThat(code.isUsable(NOW.plusSeconds(attempt))).isTrue();
            assertThat(code.getInvalidatedAt()).isNull();
        }
    }

    @Test
    void theFifthFailedAttemptInvalidatesTheCode() {
        PasswordResetCode code = issued();
        for (int attempt = 1; attempt <= 4; attempt++) {
            code.recordFailedAttempt(NOW.plusSeconds(attempt), 5);
        }
        Instant fifth = NOW.plusSeconds(5);

        code.recordFailedAttempt(fifth, 5);

        assertThat(code.getFailedAttempts()).isEqualTo(5);
        assertThat(code.getInvalidatedAt()).isEqualTo(fifth);
        assertThat(code.isUsable(fifth)).isFalse();
    }

    @Test
    void recordFailedAttemptOnAnUnusableCodeThrows() {
        PasswordResetCode expired = issued();
        PasswordResetCode used = issued();
        used.markUsed(NOW.plusSeconds(1));
        PasswordResetCode invalidated = issued();
        invalidated.invalidate(NOW.plusSeconds(1));

        assertThatThrownBy(() -> expired.recordFailedAttempt(NOW.plus(TTL), 5))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> used.recordFailedAttempt(NOW.plusSeconds(2), 5))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invalidated.recordFailedAttempt(NOW.plusSeconds(2), 5))
                .isInstanceOf(IllegalStateException.class);
        assertThat(used.getFailedAttempts()).isZero();
    }

    @Test
    void invalidateClosesAnOpenCodeEvenWhenItHasExpired() {
        PasswordResetCode open = issued();
        PasswordResetCode expired = issued();
        Instant later = NOW.plus(TTL).plusSeconds(10);

        open.invalidate(NOW.plusSeconds(3));
        expired.invalidate(later);

        assertThat(open.getInvalidatedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(open.isUsable(NOW.plusSeconds(4))).isFalse();
        assertThat(expired.getInvalidatedAt()).isEqualTo(later);
    }

    @Test
    void invalidateDoesNothingToAUsedOrAlreadyInvalidatedCode() {
        PasswordResetCode used = issued();
        used.markUsed(NOW.plusSeconds(1));
        PasswordResetCode invalidated = issued();
        invalidated.invalidate(NOW.plusSeconds(1));

        used.invalidate(NOW.plusSeconds(9));
        invalidated.invalidate(NOW.plusSeconds(9));

        assertThat(used.getInvalidatedAt()).isNull();
        assertThat(used.getUsedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(invalidated.getInvalidatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    /**
     * PostgreSQL keeps microseconds and rounds anything finer, which could push a stored expiry past the real one.
     * Times are truncated to microseconds when the code is issued, so what is stored is exactly what was computed.
     */
    @Test
    void issueTruncatesTimesToMicrosecondsSoTheStoredExpiryIsExact() {
        Instant withNanos = NOW.plusNanos(123_456_789);

        PasswordResetCode code = PasswordResetCode.issue(7L, HASH, ContactChannel.EMAIL, withNanos, TTL);

        Instant micros = NOW.plusNanos(123_456_000);
        assertThat(code.getIssuedAt()).isEqualTo(micros);
        assertThat(code.getExpiresAt()).isEqualTo(micros.plus(TTL));
        assertThat(code.isUsable(withNanos.plus(TTL))).isFalse();
    }
}
