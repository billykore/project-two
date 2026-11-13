package org.kore.myapp.core.port;

/**
 * Source of the one-time codes sent to users.
 * <p>
 * Injected so that application services never create random values themselves and stay deterministic in tests
 * (constitution Principle VIII).
 */
@FunctionalInterface
public interface ResetCodeGenerator {
    /**
     * @return a new uniformly random code of exactly 6 digits, leading zeros kept.
     */
    String newCode();
}
