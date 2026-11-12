package org.kore.myapp.api.response;

import org.kore.myapp.core.entity.User;

public record RegisterResponse(
        Long id,
        String username,
        String name,
        String email,
        String phone
) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(
                user.getId(), user.getUsername(), user.getName(), user.getEmail(), user.getPhone());
    }
}
