package org.kore.myapp.core.port;

import org.kore.myapp.core.exception.InvalidPasswordResetException;
import org.kore.myapp.core.exception.InvalidResetCodeException;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.valueobject.RequestPasswordResetCommand;
import org.kore.myapp.core.valueobject.ResetPasswordCommand;

/**
 * Business logic for resetting a forgotten password with a one-time code.
 */
public interface PasswordResetUseCase {

    /**
     * Sends a one-time code to the contact if it belongs to an account and no limit applies. Completes normally in
     * every case, so callers cannot tell whether an account exists.
     *
     * @param command validated, normalized contact.
     */
    void requestReset(RequestPasswordResetCommand command);

    /**
     * Replaces the account's password using a code sent to it, uses up the code, and ends all of its sessions.
     *
     * @param command validated contact, code, and new password.
     * @throws InvalidResetCodeException     if no account matches, it has no usable code, or the code is wrong; a
     *                                       wrong code is counted even though this is thrown.
     * @throws InvalidPasswordResetException if the new password equals the username or is one of the last 3
     *                                       passwords; the code stays usable.
     * @throws PasswordResetBusyException    if another reset of the same account holds it longer than the lock
     *                                       timeout; nothing was changed, and the caller may retry.
     */
    void resetPassword(ResetPasswordCommand command);
}
