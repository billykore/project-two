package org.kore.myapp.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "budi") @NotBlank String username,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "secret123") @NotBlank String password
) {}
