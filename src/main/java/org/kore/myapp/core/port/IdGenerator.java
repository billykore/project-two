package org.kore.myapp.core.port;

import java.util.UUID;

/**
 * Source of the identifiers that appear in domain objects and events, such as trip ids and event ids.
 * <p>
 * Injected so that application services and domain logic never call {@link UUID#randomUUID()} themselves and stay
 * deterministic in tests (constitution Principle VIII).
 */
@FunctionalInterface
public interface IdGenerator {
    /**
     * @return a new, unique identifier.
     */
    UUID newId();
}
