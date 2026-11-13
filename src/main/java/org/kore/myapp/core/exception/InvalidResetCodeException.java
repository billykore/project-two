package org.kore.myapp.core.exception;

/**
 * A reset code cannot be used. The message is the same for every cause (no such account, no usable code, wrong,
 * expired, used, superseded, or exhausted code), so it reveals nothing about the account.
 */
public class InvalidResetCodeException extends RuntimeException {
    public InvalidResetCodeException() {
        super("Invalid or expired code");
    }
}
