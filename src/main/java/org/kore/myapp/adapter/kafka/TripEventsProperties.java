package org.kore.myapp.adapter.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where and how trip events are sent to Kafka.
 *
 * @param tripEventsTopic topic that receives every trip event ({@code app.kafka.trip-events-topic}).
 * @param publishTimeout  how long to wait for the broker to acknowledge a send ({@code app.kafka.publish-timeout}).
 */
@ConfigurationProperties(prefix = "app.kafka")
public record TripEventsProperties(String tripEventsTopic, Duration publishTimeout) {
}
