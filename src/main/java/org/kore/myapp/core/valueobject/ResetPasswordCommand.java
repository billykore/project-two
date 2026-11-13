package org.kore.myapp.core.valueobject;

import org.kore.myapp.core.exception.InvalidPasswordResetException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A request to replace a forgotten password using a one-time code.
 * <p>
 * The contact follows the same rules as {@link RequestPasswordResetCommand}. The new password follows the
 * registration format rules; whether it equals the username or a recent password can only be decided once the
 * account is known.
 *
 * @param email       the email address of the account, or null.
 * @param phone       the phone number of the account, or null.
 * @param code        the 6-digit code the user received.
 * @param newPassword the plain new password; never stored, logged, or returned.
 * @throws InvalidPasswordResetException if any component breaks its rule; lists every invalid field.
 */
public record ResetPasswordCommand(String email, String phone, String code, String newPassword) {

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    public ResetPasswordCommand {
        email = ContactRules.normalizedEmail(email);
        phone = ContactRules.phoneOrNull(phone);

        List<FieldError> errors = new ArrayList<>();
        ContactRules.checkContact(email, phone, errors);
        if (code == null || !CODE.matcher(code).matches()) {
            errors.add(new FieldError("code", "must be 6 digits"));
        }
        String passwordError = PasswordRules.formatError(newPassword);
        if (passwordError != null) {
            errors.add(new FieldError("newPassword", passwordError));
        }
        if (!errors.isEmpty()) {
            throw new InvalidPasswordResetException(errors);
        }
    }

    /**
     * @return the contact that identifies the account; either registered contact detail works.
     */
    public Contact contact() {
        return ContactRules.contactOf(email, phone);
    }

    /**
     * @return a description that leaves out the code and the password, so the command is safe to log by accident.
     */
    @Override
    public String toString() {
        return "ResetPasswordCommand[email=" + email + ", phone=" + phone + ", code=***, newPassword=***]";
    }
}
