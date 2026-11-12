package org.kore.myapp.core.valueobject;

/**
 * One invalid field of a registration and the reason.
 *
 * @param field   the invalid field.
 * @param message why it is invalid, for example {@code must be at least 8 characters}.
 */
public record FieldError(RegistrationField field, String message) {
}
