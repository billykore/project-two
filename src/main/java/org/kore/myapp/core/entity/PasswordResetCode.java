package org.kore.myapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.kore.myapp.core.valueobject.ContactChannel;

import java.time.Duration;
import java.time.Instant;

/**
 * A one-time code issued to an account so its owner can reset a forgotten password.
 * <p>
 * Only a keyed hash of the code is kept. A code is open until it is used or invalidated, and usable while it is open
 * and has not expired.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5, updatable = false)
    private ContactChannel channel;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    /**
     * Builds a new, unsaved, open code.
     *
     * @param userId   the account the code belongs to.
     * @param codeHash the keyed hash of the code; never the code itself.
     * @param channel  the kind of contact the code is sent to.
     * @param now      when the code is issued.
     * @param ttl      how long the code stays valid.
     * @return an open code without an id.
     * @throws IllegalArgumentException if {@code codeHash} is null or blank.
     */
    public static PasswordResetCode issue(long userId, String codeHash, ContactChannel channel, Instant now,
                                          Duration ttl) {
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("Code hash is required");
        }
        return PasswordResetCode.builder()
                .userId(userId)
                .codeHash(codeHash)
                .channel(channel)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
    }

    /**
     * @param now the current time.
     * @return true if the code is neither used nor invalidated and has not expired yet.
     */
    public boolean isUsable(Instant now) {
        return usedAt == null && invalidatedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Uses up the code.
     *
     * @param now the current time.
     * @throws IllegalStateException if the code is not usable.
     */
    public void markUsed(Instant now) {
        requireUsable(now);
        this.usedAt = now;
    }

    /**
     * Counts a wrong code; the attempt that reaches {@code maxAttempts} invalidates the code.
     *
     * @param now         the current time.
     * @param maxAttempts how many wrong codes the code survives.
     * @throws IllegalStateException if the code is not usable.
     */
    public void recordFailedAttempt(Instant now, int maxAttempts) {
        requireUsable(now);
        this.failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            this.invalidatedAt = now;
        }
    }

    /**
     * Closes an open code, whether or not it has expired. Does nothing to a code that is already used or
     * invalidated.
     *
     * @param now the current time.
     */
    public void invalidate(Instant now) {
        if (usedAt == null && invalidatedAt == null) {
            this.invalidatedAt = now;
        }
    }

    private void requireUsable(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("Reset code is not usable");
        }
    }
}
