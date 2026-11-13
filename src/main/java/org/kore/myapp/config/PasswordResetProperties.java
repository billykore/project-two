package org.kore.myapp.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limits of the password reset, from {@code app.password-reset.*}. Turned into the domain's
 * {@code PasswordResetPolicy} in {@link ApplicationConfig}.
 */
@Validated
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(
        @NotNull Duration codeTtl,
        @Min(1) int maxAttempts,
        @NotNull Duration resendInterval,
        @Min(1) int maxCodesPerHour
) {}
