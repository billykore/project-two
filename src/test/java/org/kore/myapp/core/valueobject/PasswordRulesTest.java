package org.kore.myapp.core.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRulesTest {

    @Test
    void acceptsEightAndSeventyTwoAsciiCharacters() {
        assertThat(PasswordRules.formatError("a".repeat(8))).isNull();
        assertThat(PasswordRules.formatError("a".repeat(72))).isNull();
    }

    @Test
    void requiresAPassword() {
        assertThat(PasswordRules.formatError(null)).isEqualTo("is required");
        assertThat(PasswordRules.formatError("")).isEqualTo("is required");
    }

    @Test
    void rejectsSevenCharacters() {
        assertThat(PasswordRules.formatError("a".repeat(7))).isEqualTo("must be at least 8 characters");
    }

    @Test
    void rejectsSeventyThreeCharacters() {
        assertThat(PasswordRules.formatError("a".repeat(73))).isEqualTo("must be at most 72 characters");
    }

    @Test
    void rejectsMoreThanSeventyTwoBytesEvenWithFewerCharacters() {
        assertThat(PasswordRules.formatError("é".repeat(40)))
                .isEqualTo("must be at most 72 bytes; non-ASCII characters count as more than one");
    }

    @Test
    void keepsSpacesAndNeedsNoCompositionRules() {
        assertThat(PasswordRules.formatError("        ")).isNull();
        assertThat(PasswordRules.formatError("alllowercase")).isNull();
    }

    @Test
    void rejectsAPasswordEqualToTheUsername() {
        assertThat(PasswordRules.usernameError("budi-budi", "budi-budi"))
                .isEqualTo("must not be the same as the username");
    }

    @Test
    void acceptsAPasswordDifferentFromTheUsername() {
        assertThat(PasswordRules.usernameError("secret123", "budi")).isNull();
    }

    @Test
    void historyCoversTheCurrentPasswordAndTheTwoBeforeIt() {
        assertThat(PasswordRules.HISTORY_SIZE).isEqualTo(3);
    }
}
