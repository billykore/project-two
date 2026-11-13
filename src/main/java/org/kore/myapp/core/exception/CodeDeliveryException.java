package org.kore.myapp.core.exception;

/**
 * A one-time code could not be delivered to its contact.
 */
public class CodeDeliveryException extends RuntimeException {

    /**
     * @param message why delivery failed; never contains the code.
     */
    public CodeDeliveryException(String message) {
        super(message);
    }

    /**
     * @param message why delivery failed; never contains the code.
     * @param cause   the underlying failure.
     */
    public CodeDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
