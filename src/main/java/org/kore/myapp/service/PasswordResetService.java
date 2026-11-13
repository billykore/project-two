package org.kore.myapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kore.myapp.core.entity.PasswordHistoryEntry;
import org.kore.myapp.core.entity.PasswordResetCode;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.CodeDeliveryException;
import org.kore.myapp.core.exception.InvalidPasswordResetException;
import org.kore.myapp.core.exception.InvalidResetCodeException;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.port.PasswordHasher;
import org.kore.myapp.core.port.PasswordHistoryRepository;
import org.kore.myapp.core.port.PasswordResetCodeRepository;
import org.kore.myapp.core.port.PasswordResetUseCase;
import org.kore.myapp.core.port.ResetCodeGenerator;
import org.kore.myapp.core.port.ResetCodeHasher;
import org.kore.myapp.core.port.ResetCodeSender;
import org.kore.myapp.core.port.SessionRepository;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.core.valueobject.FieldError;
import org.kore.myapp.core.valueobject.PasswordResetPolicy;
import org.kore.myapp.core.valueobject.PasswordRules;
import org.kore.myapp.core.valueobject.RequestPasswordResetCommand;
import org.kore.myapp.core.valueobject.ResetPasswordCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService implements PasswordResetUseCase {

    private final UserRepository userRepository;

    private final PasswordResetCodeRepository codeRepository;

    private final PasswordHistoryRepository historyRepository;

    private final SessionRepository sessionRepository;

    private final PasswordHasher passwordHasher;

    private final ResetCodeHasher codeHasher;

    private final ResetCodeGenerator codeGenerator;

    private final ResetCodeSender codeSender;

    private final ResetCodeIssuer resetCodeIssuer;

    private final PasswordResetPolicy policy;

    private final Clock clock;

    /**
     * Not transactional on purpose: the code is stored by {@link ResetCodeIssuer} in its own transaction, so it is
     * committed before it is sent, and a lock failure can be absorbed here without breaking a transaction.
     */
    @Override
    public void requestReset(RequestPasswordResetCommand command) {
        Contact contact = command.contact();
        Instant now = clock.instant();
        String code = codeGenerator.newCode();
        String codeHash = codeHasher.hash(code);

        OptionalLong userId;
        try {
            userId = resetCodeIssuer.issue(contact, codeHash, now);
        } catch (PasswordResetBusyException e) {
            log.warn("password reset request skipped, account busy, channel={}", contact.channel());
            return;
        }
        if (userId.isEmpty()) {
            return;
        }
        try {
            codeSender.send(contact, code);
        } catch (CodeDeliveryException e) {
            log.warn("password reset code not delivered, userId={}, channel={}, reason={}",
                    userId.getAsLong(), contact.channel(), e.getMessage());
        }
    }

    /**
     * A wrong code is counted and saved even though the request fails, so it does not roll back for
     * {@link InvalidResetCodeException}. Any other failure rolls everything back.
     */
    @Override
    @Transactional(noRollbackFor = InvalidResetCodeException.class)
    public void resetPassword(ResetPasswordCommand command) {
        Instant now = clock.instant();
        User user = userRepository.findForPasswordReset(command.contact())
                .orElseThrow(InvalidResetCodeException::new);
        PasswordResetCode code = codeRepository.findOpen(user.getId())
                .filter(open -> open.isUsable(now))
                .orElseThrow(InvalidResetCodeException::new);
        if (!codeHasher.matches(command.code(), code.getCodeHash())) {
            code.recordFailedAttempt(now, policy.maxAttempts());
            codeRepository.update(code);
            log.info("password reset code rejected, userId={}, failedAttempts={}",
                    user.getId(), code.getFailedAttempts());
            throw new InvalidResetCodeException();
        }

        rejectUnacceptableNewPassword(user, command.newPassword());

        historyRepository.add(PasswordHistoryEntry.of(user.getId(), user.getPassword(), now));
        historyRepository.keepNewest(user.getId(), PasswordRules.HISTORY_SIZE - 1);
        user.changePassword(passwordHasher.hash(command.newPassword()));
        userRepository.update(user);
        code.markUsed(now);
        codeRepository.update(code);
        int ended = sessionRepository.revokeAllForUser(user.getId(), now);
        log.info("password reset, userId={}, sessionsEnded={}", user.getId(), ended);
    }

    /**
     * Runs only after the code is verified, so the account's history is never revealed to someone without a valid
     * code. Throwing here rolls the transaction back, which leaves the code open for another try.
     */
    private void rejectUnacceptableNewPassword(User user, String newPassword) {
        String usernameError = PasswordRules.usernameError(newPassword, user.getUsername());
        if (usernameError != null) {
            throw new InvalidPasswordResetException(List.of(new FieldError("newPassword", usernameError)));
        }
        if (matchesALastPassword(user, newPassword)) {
            throw new InvalidPasswordResetException(List.of(new FieldError(
                    "newPassword", "must not be one of your last " + PasswordRules.HISTORY_SIZE + " passwords")));
        }
    }

    /** The current password counts as one of the last passwords, so only the earlier ones come from the history. */
    private boolean matchesALastPassword(User user, String newPassword) {
        if (passwordHasher.compare(newPassword, user.getPassword())) {
            return true;
        }
        return historyRepository.findRecentHashes(user.getId(), PasswordRules.HISTORY_SIZE - 1).stream()
                .anyMatch(previous -> passwordHasher.compare(newPassword, previous));
    }
}
