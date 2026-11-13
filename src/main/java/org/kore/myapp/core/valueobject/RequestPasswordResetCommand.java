package org.kore.myapp.core.valueobject;

import org.kore.myapp.core.exception.InvalidPasswordResetException;

import java.util.ArrayList;
import java.util.List;

/**
 * A request to send a password-reset code to the email address or phone number of an account.
 * <p>
 * Exactly one of {@code email} and {@code phone} must be given; a blank value counts as absent. The email is
 * stripped and lowercased, and both follow the same format rules as registration.
 *
 * @param email the email address, or null.
 * @param phone the phone number in international form ({@code +} and 8-15 digits), or null.
 * @throws InvalidPasswordResetException if the contact breaks a rule; lists every invalid field.
 */
public record RequestPasswordResetCommand(String email, String phone) {

    public RequestPasswordResetCommand {
        email = ContactRules.normalizedEmail(email);
        phone = ContactRules.phoneOrNull(phone);

        List<FieldError> errors = new ArrayList<>();
        ContactRules.checkContact(email, phone, errors);
        if (!errors.isEmpty()) {
            throw new InvalidPasswordResetException(errors);
        }
    }

    /**
     * @return the contact the code is sent to; its channel is chosen by which of the two was given.
     */
    public Contact contact() {
        return ContactRules.contactOf(email, phone);
    }
}
