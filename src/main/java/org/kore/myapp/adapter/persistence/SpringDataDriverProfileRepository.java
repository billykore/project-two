package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataDriverProfileRepository extends JpaRepository<DriverProfile, Long> {

    Optional<DriverProfile> findByUserId(long userId);
}
