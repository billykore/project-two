package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.DriverProfile;

import java.util.Optional;

/**
 * Stores and finds {@link DriverProfile}s.
 */
public interface DriverProfileRepository {

    /**
     * @param userId id of the user who is the driver.
     * @return the driver's profile, or empty if the user is not a driver.
     */
    Optional<DriverProfile> findByUserId(long userId);

    /**
     * Stores changes to an existing driver profile.
     *
     * @param driver a profile that was loaded from this repository.
     * @return the stored profile.
     */
    DriverProfile update(DriverProfile driver);
}
