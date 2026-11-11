package org.kore.myapp.adapter.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.port.ServiceTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceAdapterTest
class JpaServiceTypeRepositoryIT {

    @Autowired
    private ServiceTypeRepository serviceTypes;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsTheSeededServiceTypesByName() {
        assertThat(serviceTypes.findActiveByName("MyRide")).map(ServiceType::getId).contains(1L);
        assertThat(serviceTypes.findActiveByName("MyCar")).map(ServiceType::getId).contains(2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MYRIDE", "myride", "Bike"})
    void nameMatchIsExactAndCaseSensitive(String name) {
        assertThat(serviceTypes.findActiveByName(name)).isEmpty();
    }

    @Test
    void ignoresInactiveServiceTypes() {
        entityManager.createNativeQuery("update service_types set active = false where name = 'MyCar'")
                .executeUpdate();
        entityManager.clear();

        assertThat(serviceTypes.findActiveByName("MyCar")).isEmpty();
    }
}
