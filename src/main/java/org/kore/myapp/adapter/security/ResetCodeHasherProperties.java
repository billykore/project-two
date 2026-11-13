package org.kore.myapp.adapter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key for hashing password-reset codes, from {@code app.password-reset.code-secret}.
 *
 * @param codeSecret Base64 of an HMAC key of at least 32 bytes.
 */
@ConfigurationProperties(prefix = "app.password-reset")
public record ResetCodeHasherProperties(String codeSecret) {

    @Override
    public String toString() {
        return "ResetCodeHasherProperties[codeSecret=***]";
    }
}
