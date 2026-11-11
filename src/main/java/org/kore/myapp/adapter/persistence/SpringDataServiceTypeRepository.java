package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    Optional<ServiceType> findByNameAndActiveTrue(String name);
}
