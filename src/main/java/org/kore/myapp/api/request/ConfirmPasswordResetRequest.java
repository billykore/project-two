package org.kore.myapp.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Body of {@code POST /auth/password-reset/confirm}. Every rule is checked by the reset command, so a missing
 * property arrives as {@code null} and is reported together with the other invalid fields.
 */
@Schema(description = "Exactly one of `email` and `phone`, plus `code` and `newPassword`.")
public record ConfirmPasswordResetRequest(
        @Schema(format = "email", maxLength = 254, example = "budi@example.com",
                description = "Email address of the account.")
        String email,
        @Schema(pattern = "^\\+[0-9]{8,15}$", example = "+6281234567890",
                description = "Phone number of the account.")
        String phone,
        @Schema(requiredMode = RequiredMode.REQUIRED, pattern = "^[0-9]{6}$", example = "004213",
                description = "The 6-digit code; leading zeros are significant.")
        String code,
        @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 8, maxLength = 72, example = "newsecret99", description = """
                Same rules as the registration password; must not equal the username or any of the account's \
                last 3 passwords.""")
        String newPassword
) {

    @Override
    public String toString() {
        return "ConfirmPasswordResetRequest[email=" + email + ", phone=" + phone + ", code=***, newPassword=***]";
    }
}
