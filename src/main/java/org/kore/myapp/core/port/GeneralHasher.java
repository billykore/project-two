package org.kore.myapp.core.port;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * General purpose hashing utilities.
 */
public interface GeneralHasher {
    /**
     * Hash a string using SHA-256.
     *
     * @param input string to hash, encoded as UTF-8.
     * @return lowercase hexadecimal representation of the SHA-256 digest.
     * @throws IllegalStateException if the SHA-256 algorithm is not available.
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
