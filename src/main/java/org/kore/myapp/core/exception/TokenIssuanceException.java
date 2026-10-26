package org.kore.myapp.core.exception;

public class TokenIssuanceException extends RuntimeException {
    public TokenIssuanceException(String message, Throwable cause) {
        super(message, cause);
    }
}