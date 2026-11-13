package org.kore.myapp.adapter.security;

import org.kore.myapp.core.port.ResetCodeHasher;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * {@link ResetCodeHasher} using HMAC-SHA256 over the UTF-8 bytes of the code.
 * <p>
 * A 6-digit code has only a million values, so a plain hash could be reversed from the database alone. The secret
 * key is never stored there, which keeps the hash useless without it.
 */
@Component
public class HmacResetCodeHasher implements ResetCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    /**
     * @param properties holds the Base64 key.
     * @throws IllegalStateException if the key is missing, not valid Base64, or shorter than 32 bytes.
     */
    public HmacResetCodeHasher(ResetCodeHasherProperties properties) {
        String encoded = properties.codeSecret();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Reset code secret (app.password-reset.code-secret) is required");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Reset code secret must be Base64", e);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("Reset code secret must be 256 bit (32 byte)");
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    /**
     * @throws IllegalStateException if HMAC-SHA256 is not available.
     */
    @Override
    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    @Override
    public boolean matches(String code, String hash) {
        if (code == null || hash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(code).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
    }
}
