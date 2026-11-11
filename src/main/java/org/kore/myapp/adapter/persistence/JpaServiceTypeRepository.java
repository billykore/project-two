package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.port.ServiceTypeRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ServiceTypeRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaServiceTypeRepository implements ServiceTypeRepository {

    private final SpringDataServiceTypeRepository serviceTypes;

    JpaServiceTypeRepository(SpringDataServiceTypeRepository serviceTypes) {
        this.serviceTypes = serviceTypes;
    }

    @Override
    public Optional<ServiceType> findActiveByName(String name) {
        return serviceTypes.findByNameAndActiveTrue(name);
    }
}
