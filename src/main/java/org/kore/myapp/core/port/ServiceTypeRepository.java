package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ServiceType} related persistence operations.
 * <p>
 * Inherits the standard CRUD operations from {@link JpaRepository}.
 */
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

}
