package org.kore.myapp.api.response;

/**
 * Answer to a reset request. It is the same for every well-formed request, so it never reveals whether an account
 * exists for the contact.
 *
 * @param message a fixed, generic sentence.
 */
public record PasswordResetRequestedResponse(String message) {

    private static final String GENERIC_MESSAGE = "If an account exists for this contact, a code has been sent to it.";

    public static PasswordResetRequestedResponse generic() {
        return new PasswordResetRequestedResponse(GENERIC_MESSAGE);
    }
}
