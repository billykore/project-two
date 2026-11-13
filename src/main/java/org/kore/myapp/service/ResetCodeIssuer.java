package org.kore.myapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kore.myapp.core.entity.PasswordResetCode;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.port.PasswordResetCodeRepository;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.core.valueobject.PasswordResetPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The transactional step of requesting a reset: finds and locks the account and stores a new code.
 * <p>
 * It is separate from {@link PasswordResetService#requestReset} so that a failure to take the account lock ends
 * only this transaction, and the caller can still answer normally outside it. Delivery happens after it commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResetCodeIssuer {

    private final UserRepository userRepository;

    private final PasswordResetCodeRepository codeRepository;

    private final PasswordResetPolicy policy;

    /**
     * Stores a new code for the account that owns the contact, if there is one.
     *
     * @param contact  the email address or phone number the code is requested for.
     * @param codeHash the keyed hash of the new code.
     * @param now      the current time.
     * @return the account's id if a code was stored, or empty if no code was issued.
     * @throws PasswordResetBusyException if the account is locked by another reset for too long.
     */
    @Transactional
    public OptionalLong issue(Contact contact, String codeHash, Instant now) {
        Optional<User> found = userRepository.findForPasswordReset(contact);
        if (found.isEmpty()) {
            log.debug("password reset requested for an unknown contact, channel={}", contact.channel());
            return OptionalLong.empty();
        }
        long userId = found.get().getId();
        if (limitApplies(userId, now)) {
            log.info("password reset code not issued, limit reached, userId={}", userId);
            return OptionalLong.empty();
        }
        codeRepository.findOpen(userId).ifPresent(open -> {
            open.invalidate(now);
            codeRepository.update(open);
        });
        codeRepository.add(PasswordResetCode.issue(userId, codeHash, contact.channel(), now, policy.codeTtl()));
        return OptionalLong.of(userId);
    }

    /**
     * Either a code was issued less than the resend interval ago, or the hourly cap is reached. The count is
     * inclusive of its start time, so the resend window starts one millisecond after the interval: a code issued
     * exactly one interval ago no longer blocks.
     */
    private boolean limitApplies(long userId, Instant now) {
        Instant resendWindowStart = now.minus(policy.resendInterval()).plusMillis(1);
        if (codeRepository.countIssuedSince(userId, resendWindowStart) > 0) {
            return true;
        }
        return codeRepository.countIssuedSince(userId, now.minus(Duration.ofHours(1))) >= policy.maxCodesPerHour();
    }
}
