package org.kore.myapp.core.port.archfixture;

import jakarta.persistence.EntityManager;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Architecture-rule fixture (AR-03, AR-04, AR-05): ports stay framework-free. Never used at runtime;
 * {@link NoRepositoryBean} keeps Spring Data's repository scan in test contexts from instantiating it.
 */
@NoRepositoryBean
public interface PortWithJpa extends Repository<Object, Long> {
    EntityManager entityManager();
}
