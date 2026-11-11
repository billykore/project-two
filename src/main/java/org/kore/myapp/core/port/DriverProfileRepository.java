package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link DriverProfile} related persistence operations.
 * <p>
 * Inherits the standard CRUD operations from {@link JpaRepository}.
 */
public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {

    Optional<DriverProfile> findByUserId(Long userId);
}
