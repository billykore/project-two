package org.kore.myapp.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code POST /auth/password-reset/request}. Exactly one of {@code email} and {@code phone} is expected;
 * the reset command checks it, so a missing property arrives as {@code null} and is reported with the rest.
 */
@Schema(description = "Exactly one of `email` and `phone`; a blank value counts as absent.")
public record RequestPasswordResetRequest(
        @Schema(format = "email", maxLength = 254, example = "budi@example.com",
                description = "Email address of the account; trimmed and compared ignoring case.")
        String email,
        @Schema(pattern = "^\\+[0-9]{8,15}$", example = "+6281234567890",
                description = "Phone number of the account, `+` and 8-15 digits.")
        String phone
) {
}
