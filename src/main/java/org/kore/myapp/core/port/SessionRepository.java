package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Session} related persistence operations.
 * <p>
 * Inherits the standard CRUD operations from {@link JpaRepository}.
 */
public interface SessionRepository extends JpaRepository<Session, Long> {

}
