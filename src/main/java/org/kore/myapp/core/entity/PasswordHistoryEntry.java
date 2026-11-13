package org.kore.myapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A password an account had before a reset, kept only as its one-way hash so a new password can be compared with
 * the account's latest ones.
 */
@Entity
@Table(name = "password_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "password_hash", nullable = false, updatable = false)
    private String passwordHash;

    @Column(name = "replaced_at", nullable = false, updatable = false)
    private Instant replacedAt;

    /**
     * Builds a new, unsaved entry.
     *
     * @param userId       the account.
     * @param passwordHash the hash of the password that stopped being current.
     * @param replacedAt   when it stopped being current.
     * @return an entry without an id.
     * @throws IllegalArgumentException if {@code passwordHash} is null or blank, or {@code replacedAt} is null.
     */
    public static PasswordHistoryEntry of(long userId, String passwordHash, Instant replacedAt) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        if (replacedAt == null) {
            throw new IllegalArgumentException("Replacement time is required");
        }
        return PasswordHistoryEntry.builder()
                .userId(userId)
                .passwordHash(passwordHash)
                .replacedAt(replacedAt)
                .build();
    }
}
