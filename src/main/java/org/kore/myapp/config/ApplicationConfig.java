package org.kore.myapp.config;

import org.kore.myapp.adapter.kafka.TripEventsProperties;
import org.kore.myapp.adapter.persistence.PasswordResetLockProperties;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.port.IdGenerator;
import org.kore.myapp.core.port.ResetCodeGenerator;
import org.kore.myapp.core.valueobject.PasswordResetPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Wires the application's sources of time and identifiers, and its framework-free domain services.
 */
@Configuration
@EnableConfigurationProperties({
        TripEventsProperties.class, PasswordResetProperties.class, PasswordResetLockProperties.class})
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IdGenerator idGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    ResetCodeGenerator resetCodeGenerator() {
        SecureRandom random = new SecureRandom();
        return () -> String.format("%06d", random.nextInt(1_000_000));
    }

    @Bean
    PasswordResetPolicy passwordResetPolicy(PasswordResetProperties props) {
        return new PasswordResetPolicy(
                props.codeTtl(), props.maxAttempts(), props.resendInterval(), props.maxCodesPerHour());
    }

    @Bean
    FareCalculator fareCalculator(FareProperties fares) {
        return new FareCalculator(fares.perMeter(), fares.minimum());
    }
}
