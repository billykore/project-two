package org.kore.myapp.core.port;

/**
 * One-way, keyed hashing of one-time reset codes, so that only a hash of a code is ever stored.
 */
public interface ResetCodeHasher {
    /**
     * Hashes a code for storage.
     *
     * @param code a 6-digit code.
     * @return a keyed one-way hash of the code, as 64 lowercase hexadecimal characters.
     */
    String hash(String code);

    /**
     * Checks a code against a stored hash, in constant time.
     *
     * @param code a code submitted by a user.
     * @param hash a hash returned earlier by {@link #hash(String)}.
     * @return true if {@code code} hashes to {@code hash}.
     */
    boolean matches(String code, String hash);
}
