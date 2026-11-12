package org.kore.myapp.api.request;

/**
 * Body of {@code POST /auth/register}. Every rule is checked by the registration command, so a missing property
 * arrives as {@code null} and is reported together with the other invalid fields.
 */
public record RegisterRequest(
        String username,
        String password,
        String name,
        String email,
        String phone
) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=***]";
    }
}
