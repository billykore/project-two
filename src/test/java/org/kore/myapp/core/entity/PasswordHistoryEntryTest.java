package org.kore.myapp.core.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHistoryEntryTest {

    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00Z");

    @Test
    void ofCopiesTheUserTheHashAndTheTime() {
        PasswordHistoryEntry entry = PasswordHistoryEntry.of(7L, "bcrypt-hash", NOW);

        assertThat(entry.getId()).isNull();
        assertThat(entry.getUserId()).isEqualTo(7L);
        assertThat(entry.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(entry.getReplacedAt()).isEqualTo(NOW);
    }

    @Test
    void ofRejectsABlankHash() {
        assertThatThrownBy(() -> PasswordHistoryEntry.of(7L, " ", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordHistoryEntry.of(7L, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofRejectsAMissingTime() {
        assertThatThrownBy(() -> PasswordHistoryEntry.of(7L, "bcrypt-hash", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
