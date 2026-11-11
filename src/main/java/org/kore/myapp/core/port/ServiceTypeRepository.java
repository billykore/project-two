package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.ServiceType;

import java.util.Optional;

/**
 * Finds the {@link ServiceType}s customers can order.
 */
public interface ServiceTypeRepository {

    /**
     * @param name name of the service type, such as {@code MyRide}; matched exactly, case-sensitive.
     * @return the active service type with that name, or empty if there is none.
     */
    Optional<ServiceType> findActiveByName(String name);
}
