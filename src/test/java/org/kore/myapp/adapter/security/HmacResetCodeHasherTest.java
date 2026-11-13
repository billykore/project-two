package org.kore.myapp.adapter.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacResetCodeHasherTest {

    private static final String KEY_A = keyOf('a', 32);
    private static final String KEY_B = keyOf('b', 32);

    private final HmacResetCodeHasher hasher = hasherWith(KEY_A);

    @Test
    void hashIsDeterministicSixtyFourLowercaseHexCharacters() {
        String first = hasher.hash("004213");

        assertThat(first).matches("[0-9a-f]{64}");
        assertThat(hasher.hash("004213")).isEqualTo(first);
        assertThat(hasher.hash("004214")).isNotEqualTo(first);
    }

    @Test
    void theSameCodeHashesDifferentlyUnderADifferentKey() {
        assertThat(hasherWith(KEY_B).hash("004213")).isNotEqualTo(hasher.hash("004213"));
    }

    @Test
    void matchesAcceptsTheRightCodeOnly() {
        String hash = hasher.hash("123456");

        assertThat(hasher.matches("123456", hash)).isTrue();
        assertThat(hasher.matches("123457", hash)).isFalse();
        assertThat(hasher.matches("123456", "not-a-hash")).isFalse();
        assertThat(hasher.matches("123456", null)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
    }

    @Test
    void aHashMadeUnderAnotherKeyDoesNotMatch() {
        assertThat(hasher.matches("123456", hasherWith(KEY_B).hash("123456"))).isFalse();
    }

    @Test
    void aMissingKeyIsRejected() {
        assertThatThrownBy(() -> hasherWith(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> hasherWith("  ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aKeyThatIsNotBase64IsRejected() {
        assertThatThrownBy(() -> hasherWith("not base64 !!"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    void aKeyShorterThan32BytesIsRejected() {
        assertThatThrownBy(() -> hasherWith(keyOf('a', 31)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 byte");
    }

    @Test
    void thePropertiesDoNotPrintTheKey() {
        assertThat(new ResetCodeHasherProperties(KEY_A).toString()).doesNotContain(KEY_A);
    }

    private static HmacResetCodeHasher hasherWith(String key) {
        return new HmacResetCodeHasher(new ResetCodeHasherProperties(key));
    }

    private static String keyOf(char c, int bytes) {
        return Base64.getEncoder().encodeToString(String.valueOf(c).repeat(bytes).getBytes());
    }
}
