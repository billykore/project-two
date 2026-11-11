package org.kore.myapp.adapter.persistence;

import org.kore.myapp.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JPA slice against a real PostgreSQL container with the real Flyway migrations, plus every persistence adapter.
 * All persistence integration tests use this one annotation so they share a single cached Spring context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        TestcontainersConfiguration.class,
        PersistenceFixtures.class,
        JpaTripRepository.class,
        JpaTripOfferRepository.class,
        JpaDriverProfileRepository.class,
        JpaUserRepository.class,
        JpaSessionRepository.class,
        JpaServiceTypeRepository.class
})
@interface PersistenceAdapterTest {
}
