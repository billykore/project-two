package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.DriverProfile;
import org.kore.myapp.core.port.DriverProfileRepository;
import org.kore.myapp.core.valueobject.AvailabilityStatus;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceAdapterTest
class JpaDriverProfileRepositoryIT {

    @Autowired
    private DriverProfileRepository drivers;

    @Autowired
    private PersistenceFixtures db;

    @Test
    void findByUserIdFindsTheDriversProfile() {
        long userId = db.insertUser("driver");
        long driverId = db.insertDriver(userId);
        db.flushAndClear();

        assertThat(drivers.findByUserId(userId)).map(DriverProfile::getId).contains(driverId);
    }

    @Test
    void findByUserIdIsEmptyForUsersWhoAreNotDrivers() {
        long userId = db.insertUser("customer");
        db.flushAndClear();

        assertThat(drivers.findByUserId(userId)).isEmpty();
    }

    @Test
    void updatePersistsAvailabilityAndIncrementsVersion() {
        long userId = db.insertUser("driver");
        db.insertDriver(userId);
        db.flushAndClear();
        DriverProfile driver = drivers.findByUserId(userId).orElseThrow();

        driver.startTrip();
        drivers.update(driver);
        db.flushAndClear();

        DriverProfile reloaded = drivers.findByUserId(userId).orElseThrow();
        assertThat(reloaded.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_TRIP);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }
}
