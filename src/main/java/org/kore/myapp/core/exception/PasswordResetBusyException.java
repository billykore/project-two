package org.kore.myapp.core.exception;

/**
 * Another password-reset operation on the same account is in progress and did not finish in time. Nothing was
 * changed, and the caller may try again.
 */
public class PasswordResetBusyException extends RuntimeException {

    public PasswordResetBusyException() {
        super("Another password reset for this account is in progress; try again");
    }

    /**
     * @param cause the infrastructure failure that was translated, such as a lock timeout.
     */
    public PasswordResetBusyException(Throwable cause) {
        super("Another password reset for this account is in progress; try again", cause);
    }
}
