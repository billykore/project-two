package org.kore.myapp.core.valueobject;

import java.time.Duration;

/**
 * The tunable limits of the password reset.
 *
 * @param codeTtl          how long a code stays valid after it is issued.
 * @param maxAttempts      how many wrong codes invalidate the current code.
 * @param resendInterval   the minimum time between two codes for the same account.
 * @param maxCodesPerHour  how many codes one account may be issued in a rolling hour.
 * @throws IllegalArgumentException if a duration is missing or not positive, or a count is below 1.
 */
public record PasswordResetPolicy(Duration codeTtl, int maxAttempts, Duration resendInterval, int maxCodesPerHour) {

    public PasswordResetPolicy {
        requirePositive(codeTtl, "codeTtl");
        requirePositive(resendInterval, "resendInterval");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (maxCodesPerHour < 1) {
            throw new IllegalArgumentException("maxCodesPerHour must be at least 1");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }
}
