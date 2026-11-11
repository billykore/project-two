package org.kore.myapp.core.port;

/**
 * One-way hashing of access tokens, so that only a hash of a token is ever stored.
 */
public interface TokenHasher {
    /**
     * Hashes an access token for storage.
     *
     * @param token the access token.
     * @return the hash, as lowercase hexadecimal.
     */
    String hash(String token);
}
