package org.kore.myapp.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import org.kore.myapp.core.entity.User;

public record RegisterResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "42") Long id,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "budi") String username,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "Budi Santoso") String name,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "budi@example.com") String email,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "+6281234567890") String phone
) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(
                user.getId(), user.getUsername(), user.getName(), user.getEmail(), user.getPhone());
    }
}
