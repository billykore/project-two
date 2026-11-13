package org.kore.myapp.api.request;

/**
 * Body of {@code POST /auth/password-reset/request}. Exactly one of {@code email} and {@code phone} is expected;
 * the reset command checks it, so a missing property arrives as {@code null} and is reported with the rest.
 */
public record RequestPasswordResetRequest(String email, String phone) {
}
