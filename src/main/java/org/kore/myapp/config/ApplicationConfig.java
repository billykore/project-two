package org.kore.myapp.config;

import org.kore.myapp.adapter.kafka.TripEventsProperties;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.port.IdGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

/**
 * Wires the application's sources of time and identifiers, and its framework-free domain services.
 */
@Configuration
@EnableConfigurationProperties(TripEventsProperties.class)
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
    FareCalculator fareCalculator(FareProperties fares) {
        return new FareCalculator(fares.perMeter(), fares.minimum());
    }
}
