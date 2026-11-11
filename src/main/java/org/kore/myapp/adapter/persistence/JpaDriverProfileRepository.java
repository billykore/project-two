package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.DriverProfile;
import org.kore.myapp.core.port.DriverProfileRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link DriverProfileRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaDriverProfileRepository implements DriverProfileRepository {

    private final SpringDataDriverProfileRepository drivers;

    JpaDriverProfileRepository(SpringDataDriverProfileRepository drivers) {
        this.drivers = drivers;
    }

    @Override
    public Optional<DriverProfile> findByUserId(long userId) {
        return drivers.findByUserId(userId);
    }

    @Override
    public DriverProfile update(DriverProfile driver) {
        return drivers.save(driver);
    }
}
