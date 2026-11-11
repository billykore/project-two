package org.kore.myapp.core.exception;

/**
 * Thrown when a request can't be attributed to a known user, for example because its token carries no usable user
 * id. Nothing may be done on behalf of such a caller.
 */
public class UnidentifiedCallerException extends RuntimeException {
    public UnidentifiedCallerException(String message) {
        super(message);
    }
}
