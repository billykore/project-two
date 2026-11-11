package org.kore.myapp.adapter.security;

import org.kore.myapp.core.port.TokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@link TokenHasher} using SHA-256 over the UTF-8 bytes of the token.
 */
@Component
public class Sha256TokenHasher implements TokenHasher {

    /**
     * @throws IllegalStateException if the SHA-256 algorithm is not available.
     */
    @Override
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
