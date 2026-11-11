package org.kore.myapp.core.archfixture;

import java.time.Instant;
import java.util.UUID;

/** Architecture-rule fixture (AR-07): core must not read the clock or generate ids itself. Never used at runtime. */
public class CoreCallsClock {
    Instant now() {
        return Instant.now();
    }

    UUID id() {
        return UUID.randomUUID();
    }
}
