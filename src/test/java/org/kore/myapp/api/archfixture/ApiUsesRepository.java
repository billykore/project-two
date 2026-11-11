package org.kore.myapp.api.archfixture;

import org.kore.myapp.core.port.TripRepository;

/** Architecture-rule fixture (AR-09): inbound adapters call use cases, not outbound ports. Never used at runtime. */
public class ApiUsesRepository {
    TripRepository trips;
}
