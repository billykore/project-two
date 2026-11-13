package org.kore.myapp.api.request;

/**
 * Body of {@code POST /auth/password-reset/confirm}. Every rule is checked by the reset command, so a missing
 * property arrives as {@code null} and is reported together with the other invalid fields.
 */
public record ConfirmPasswordResetRequest(String email, String phone, String code, String newPassword) {

    @Override
    public String toString() {
        return "ConfirmPasswordResetRequest[email=" + email + ", phone=" + phone + ", code=***, newPassword=***]";
    }
}
