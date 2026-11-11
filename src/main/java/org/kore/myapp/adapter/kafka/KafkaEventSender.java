package org.kore.myapp.adapter.kafka;

import org.kore.myapp.core.exception.EventPublishException;
import org.kore.myapp.core.valueobject.Event;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/**
 * Sends an event to Kafka as JSON and waits for the broker to acknowledge it.
 * <p>
 * The message key is the event's {@link Event#aggregateId()}, so all events of one aggregate land in the same
 * partition and keep their order.
 */
@Component
class KafkaEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper eventMapper;
    private final TripEventsProperties properties;

    KafkaEventSender(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                     TripEventsProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.eventMapper = objectMapper.rebuild().addMixIn(Event.class, EventJsonMixin.class).build();
        this.properties = properties;
    }

    /**
     * @throws EventPublishException if the event can't be serialized or the broker doesn't acknowledge it in time.
     */
    void send(Event event) {
        String topic = properties.tripEventsTopic();
        try {
            kafkaTemplate
                    .send(topic, event.aggregateId(), serialize(event))
                    .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EventPublishException(
                    "Failed to publish " + event.getClass().getSimpleName() + " to topic " + topic, e);
        }
    }

    String serialize(Event event) {
        return eventMapper.writeValueAsString(event);
    }
}
