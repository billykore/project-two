package org.kore.myapp.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Answer to a reset request. It is the same for every well-formed request, so it never reveals whether an account
 * exists for the contact.
 *
 * @param message a fixed, generic sentence.
 */
public record PasswordResetRequestedResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "If an account exists for this contact, a code has been sent to it.")
        String message
) {

    private static final String GENERIC_MESSAGE = "If an account exists for this contact, a code has been sent to it.";

    public static PasswordResetRequestedResponse generic() {
        return new PasswordResetRequestedResponse(GENERIC_MESSAGE);
    }
}
