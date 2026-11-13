package org.kore.myapp.core.valueobject;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The format rules for email addresses and phone numbers, shared by registration and password reset so both accept
 * exactly the same values.
 */
final class ContactRules {

    static final int EMAIL_MAX = 254;

    private static final Pattern PHONE = Pattern.compile("\\+[0-9]{8,15}");
    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@(?:" + LABEL + "\\.)+" + LABEL);

    private static final String EITHER = "provide either email or phone";

    private ContactRules() {
    }

    /**
     * @param normalizedEmail a non-empty email address, already stripped and lowercased.
     * @return why it is refused, or null if it is acceptable.
     */
    static String emailFormatError(String normalizedEmail) {
        if (normalizedEmail.length() > EMAIL_MAX || !EMAIL.matcher(normalizedEmail).matches()) {
            return "must be a valid email address";
        }
        return null;
    }

    /**
     * @param phone a non-empty phone number.
     * @return why it is refused, or null if it is acceptable.
     */
    static String phoneFormatError(String phone) {
        if (!PHONE.matcher(phone).matches()) {
            return "must be in international format, e.g. +6281234567890";
        }
        return null;
    }

    /**
     * @param value a possibly blank value.
     * @return the value stripped and lowercased, or null if it is null or blank.
     */
    static String normalizedEmail(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * @param value a possibly blank value.
     * @return the value unchanged, or null if it is null or blank.
     */
    static String phoneOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Checks that exactly one of the two is present and well formed.
     *
     * @param email  the normalized email, or null if absent.
     * @param phone  the phone number, or null if absent.
     * @param errors receives one error per problem, in the order email, phone.
     */
    static void checkContact(String email, String phone, List<FieldError> errors) {
        if ((email == null) == (phone == null)) {
            errors.add(new FieldError("email", EITHER));
            errors.add(new FieldError("phone", EITHER));
            return;
        }
        if (email != null) {
            addIfPresent(errors, "email", emailFormatError(email));
        } else {
            addIfPresent(errors, "phone", phoneFormatError(phone));
        }
    }

    /**
     * @param email the validated normalized email, or null if absent.
     * @param phone the validated phone, or null if absent.
     * @return the contact that was given.
     */
    static Contact contactOf(String email, String phone) {
        return email != null
                ? new Contact(ContactChannel.EMAIL, email)
                : new Contact(ContactChannel.PHONE, phone);
    }

    private static void addIfPresent(List<FieldError> errors, String field, String message) {
        if (message != null) {
            errors.add(new FieldError(field, message));
        }
    }
}
