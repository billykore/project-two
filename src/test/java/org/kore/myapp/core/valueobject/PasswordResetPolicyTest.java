package org.kore.myapp.core.valueobject;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetPolicyTest {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    @Test
    void keepsValidValues() {
        PasswordResetPolicy policy = new PasswordResetPolicy(FIVE_MINUTES, 5, ONE_MINUTE, 5);

        assertThat(policy.codeTtl()).isEqualTo(FIVE_MINUTES);
        assertThat(policy.maxAttempts()).isEqualTo(5);
        assertThat(policy.resendInterval()).isEqualTo(ONE_MINUTE);
        assertThat(policy.maxCodesPerHour()).isEqualTo(5);
    }

    @Test
    void rejectsAMissingOrNonPositiveCodeTtl() {
        assertThatThrownBy(() -> new PasswordResetPolicy(null, 5, ONE_MINUTE, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("codeTtl");
        assertThatThrownBy(() -> new PasswordResetPolicy(Duration.ZERO, 5, ONE_MINUTE, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("codeTtl");
        assertThatThrownBy(() -> new PasswordResetPolicy(Duration.ofSeconds(-1), 5, ONE_MINUTE, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("codeTtl");
    }

    @Test
    void rejectsAMissingOrNonPositiveResendInterval() {
        assertThatThrownBy(() -> new PasswordResetPolicy(FIVE_MINUTES, 5, null, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resendInterval");
        assertThatThrownBy(() -> new PasswordResetPolicy(FIVE_MINUTES, 5, Duration.ZERO, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resendInterval");
    }

    @Test
    void rejectsCountsBelowOne() {
        assertThatThrownBy(() -> new PasswordResetPolicy(FIVE_MINUTES, 0, ONE_MINUTE, 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxAttempts");
        assertThatThrownBy(() -> new PasswordResetPolicy(FIVE_MINUTES, 5, ONE_MINUTE, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxCodesPerHour");
    }
}
