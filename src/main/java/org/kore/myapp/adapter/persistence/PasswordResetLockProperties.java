package org.kore.myapp.adapter.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How long a password-reset operation waits for another one on the same account, from
 * {@code app.password-reset.lock-timeout}.
 *
 * @param lockTimeout the longest wait before the account is reported as busy.
 */
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetLockProperties(@DefaultValue("15s") Duration lockTimeout) {
}
