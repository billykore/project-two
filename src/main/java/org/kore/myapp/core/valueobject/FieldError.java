package org.kore.myapp.core.valueobject;

/**
 * One invalid field of a request and the reason.
 *
 * @param field   the name clients use for the invalid field, for example {@code password} or {@code newPassword}.
 * @param message why it is invalid, for example {@code must be at least 8 characters}.
 */
public record FieldError(String field, String message) {

    /**
     * @param field   the invalid registration field; stored as the name clients send.
     * @param message why it is invalid.
     */
    public FieldError(RegistrationField field, String message) {
        this(field.jsonName(), message);
    }
}
