package org.kore.myapp.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Body of {@code POST /auth/register}. Every rule is checked by the registration command, so a missing property
 * arrives as {@code null} and is reported together with the other invalid fields.
 */
public record RegisterRequest(
        @Schema(requiredMode = RequiredMode.REQUIRED, pattern = "^[a-z0-9._-]{3,50}$", example = "budi",
                description = "3-50 lowercase letters, digits, `.`, `_`, `-`. Unique.")
        String username,
        @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 8, maxLength = 72, example = "secret123", description = """
                8-72 characters and at most 72 bytes in UTF-8; not the same as the username; kept exactly as \
                typed. No composition rules.""")
        String password,
        @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 100, example = "Budi Santoso",
                description = "1-100 characters after trimming.")
        String name,
        @Schema(requiredMode = RequiredMode.REQUIRED, format = "email", maxLength = 254, example = "budi@example.com",
                description = "Stored in lowercase; unique, ignoring case.")
        String email,
        @Schema(requiredMode = RequiredMode.REQUIRED, pattern = "^\\+[0-9]{8,15}$", example = "+6281234567890",
                description = "International form, `+` and 8-15 digits, no separators. Unique.")
        String phone
) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=***]";
    }
}
