package org.kore.myapp.adapter;

import org.kore.myapp.core.exception.EventPublishException;
import org.kore.myapp.core.port.EventPublisher;
import org.kore.myapp.core.valueobject.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link EventPublisher} that publishes events as JSON to Kafka.
 * <p>
 * The message key is the event's {@link Event#aggregateId()}, so all events of one aggregate land in the same
 * partition and keep their order.
 */
@Component
public class KafkaPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Duration timeout;

    public KafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.trip-events-topic}") String topic,
            @Value("${app.kafka.publish-timeout}") Duration timeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.timeout = timeout;
    }

    @Override
    public void publish(Event event) {
        try {
            kafkaTemplate
                    .send(topic, event.aggregateId(), objectMapper.writeValueAsString(event))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new EventPublishException(
                    "Failed to publish " + event.getClass().getSimpleName() + " to topic " + topic, e);
        }
    }
}
