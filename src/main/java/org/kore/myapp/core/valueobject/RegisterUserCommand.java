package org.kore.myapp.core.valueobject;

import org.kore.myapp.core.exception.InvalidRegistrationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Data required to register a customer account.
 * <p>
 * The name is stripped and the email address is stripped and lowercased, so everything downstream compares and
 * stores one canonical form. Every rule is checked here, and all violations are reported together.
 *
 * @param username the login name: 3-50 characters of lowercase letters, digits, {@code .}, {@code _} or {@code -}.
 * @param password the plain password, 8-72 characters and at most 72 UTF-8 bytes (the limit of BCrypt) and not the
 *                 username; never stored, logged, or returned.
 * @param name     the display name, 1-100 characters after stripping.
 * @param email    the email address, at most 254 characters.
 * @param phone    the phone number in international form: {@code +} and 8-15 digits.
 * @throws InvalidRegistrationException if any component breaks its rule; lists every invalid field once.
 */
public record RegisterUserCommand(
        String username,
        String password,
        String name,
        String email,
        String phone
) {
    static final int USERNAME_MAX = 50;
    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX_CHARACTERS = 72;
    static final int PASSWORD_MAX_BYTES = 72;
    static final int NAME_MAX = 100;
    static final int EMAIL_MAX = 254;

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3," + USERNAME_MAX + "}");
    private static final Pattern PHONE = Pattern.compile("\\+[0-9]{8,15}");
    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@(?:" + LABEL + "\\.)+" + LABEL);

    public RegisterUserCommand {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);

        List<FieldError> errors = new ArrayList<>();
        String usernameError = usernameError(username);
        addIfPresent(errors, RegistrationField.USERNAME, usernameError);
        addIfPresent(errors, RegistrationField.PASSWORD, passwordError(password, usernameError == null ? username : null));
        addIfPresent(errors, RegistrationField.NAME, nameError(name));
        addIfPresent(errors, RegistrationField.EMAIL, emailError(email));
        addIfPresent(errors, RegistrationField.PHONE, phoneError(phone));
        if (!errors.isEmpty()) {
            throw new InvalidRegistrationException(errors);
        }
    }

    /**
     * @return a description that leaves out the password, so the command is safe to log by accident.
     */
    @Override
    public String toString() {
        return "RegisterUserCommand[username=" + username + ", password=***, name=" + name
                + ", email=" + email + ", phone=" + phone + "]";
    }

    private static String usernameError(String username) {
        if (username == null || username.isBlank()) {
            return "is required";
        }
        if (!USERNAME.matcher(username).matches()) {
            return "must be 3-" + USERNAME_MAX + " characters: lowercase letters, digits, '.', '_' or '-'";
        }
        return null;
    }

    /**
     * @param validUsername the username if it is valid, so the password can be compared with it; otherwise null.
     */
    private static String passwordError(String password, String validUsername) {
        if (password == null || password.isEmpty()) {
            return "is required";
        }
        int characters = password.codePointCount(0, password.length());
        if (characters < PASSWORD_MIN) {
            return "must be at least " + PASSWORD_MIN + " characters";
        }
        if (characters > PASSWORD_MAX_CHARACTERS) {
            return "must be at most " + PASSWORD_MAX_CHARACTERS + " characters";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return "must be at most " + PASSWORD_MAX_BYTES + " bytes; non-ASCII characters count as more than one";
        }
        if (password.equals(validUsername)) {
            return "must not be the same as the username";
        }
        return null;
    }

    private static String nameError(String strippedName) {
        if (strippedName == null || strippedName.isEmpty()) {
            return "is required";
        }
        if (strippedName.length() > NAME_MAX) {
            return "must be at most " + NAME_MAX + " characters";
        }
        return null;
    }

    private static String emailError(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isEmpty()) {
            return "is required";
        }
        if (normalizedEmail.length() > EMAIL_MAX || !EMAIL.matcher(normalizedEmail).matches()) {
            return "must be a valid email address";
        }
        return null;
    }

    private static String phoneError(String phone) {
        if (phone == null || phone.isBlank()) {
            return "is required";
        }
        if (!PHONE.matcher(phone).matches()) {
            return "must be in international format, e.g. +6281234567890";
        }
        return null;
    }

    private static void addIfPresent(List<FieldError> errors, RegistrationField field, String message) {
        if (message != null) {
            errors.add(new FieldError(field, message));
        }
    }
}
