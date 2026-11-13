package org.kore.myapp.core.valueobject;

import java.nio.charset.StandardCharsets;

/**
 * The rules every new password must follow, whether it is chosen at registration or when resetting a forgotten
 * one. Each check returns the reason a password is refused, or {@code null} when the rule holds.
 */
public final class PasswordRules {

    /** A new password must differ from this many of the account's latest passwords, the current one included. */
    public static final int HISTORY_SIZE = 3;

    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX_CHARACTERS = 72;
    static final int PASSWORD_MAX_BYTES = 72;

    private PasswordRules() {
    }

    /**
     * Checks presence and length: at least 8 and at most 72 characters, and at most 72 UTF-8 bytes (the limit of
     * BCrypt). There are no composition rules.
     *
     * @param password the plain password.
     * @return why the password is refused, or null if its format is acceptable.
     */
    public static String formatError(String password) {
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
        return null;
    }

    /**
     * @param password the plain password.
     * @param username the account's username.
     * @return why the password is refused, or null if it differs from the username.
     */
    public static String usernameError(String password, String username) {
        if (password != null && password.equals(username)) {
            return "must not be the same as the username";
        }
        return null;
    }
}
