package org.kore.myapp.adapter.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.TestcontainersConfiguration;
import org.kore.myapp.core.port.EventPublisher;
import org.kore.myapp.core.valueobject.CancelledBy;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Event;
import org.kore.myapp.core.valueobject.Money;
import org.kore.myapp.core.valueobject.TripEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kore.myapp.adapter.kafka.EventJsonContractTest.EVENT_ID;
import static org.kore.myapp.adapter.kafka.EventJsonContractTest.OCCURRED_AT;

/**
 * Event delivery through the {@link EventPublisher} port against a real Kafka broker (contracts/events.md):
 * topic, key and payload of every event type, delivery only after commit, and nothing after a rollback.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AfterCommitEventPublisherIT {

    private static final String TOPIC = "trip-events";

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void subscribe() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    /** Regression test: offer events used to be sent while the transaction was still open. */
    @Test
    void eventsOfARolledBackTransactionAreNeverDelivered() {
        UUID assignedTrip = UUID.randomUUID();
        UUID rejectedTrip = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new TripEvent.DriverAssigned(EVENT_ID, OCCURRED_AT, assignedTrip, 7));
            eventPublisher.publish(new TripEvent.Rejected(EVENT_ID, OCCURRED_AT, rejectedTrip));
            status.setRollbackOnly();
        });

        assertThat(poll(Set.of(assignedTrip, rejectedTrip), Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void eventsAreNotDeliveredBeforeTheTransactionCommits() {
        UUID tripId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new TripEvent.Rejected(EVENT_ID, OCCURRED_AT, tripId));
            assertThat(poll(Set.of(tripId), Duration.ofSeconds(1))).as("delivered before commit").isEmpty();
        });

        assertThat(pollUntil(Set.of(tripId), 1)).hasSize(1);
    }

    @Test
    void everyEventTypeIsDeliveredOnceAfterCommitWithItsTopicKeyAndPayload() throws Exception {
        Map<String, Event> events = Map.of(
                "requested", new TripEvent.Requested(EVENT_ID, OCCURRED_AT, UUID.randomUUID(), 42, 1,
                        new Coordinate(-6.1754, 106.8272)),
                "driver-assigned", new TripEvent.DriverAssigned(EVENT_ID, OCCURRED_AT, UUID.randomUUID(), 7),
                "rejected", new TripEvent.Rejected(EVENT_ID, OCCURRED_AT, UUID.randomUUID()),
                "completed", new TripEvent.Completed(EVENT_ID, OCCURRED_AT, UUID.randomUUID(), new Money(25_000)),
                "cancelled", new TripEvent.Cancelled(EVENT_ID, OCCURRED_AT, UUID.randomUUID(), CancelledBy.CUSTOMER));

        transactionTemplate.executeWithoutResult(status -> events.values().forEach(eventPublisher::publish));

        Set<UUID> tripIds = new java.util.HashSet<>();
        events.values().forEach(event -> tripIds.add(((TripEvent) event).tripId()));
        List<ConsumerRecord<String, String>> records = pollUntil(tripIds, events.size());
        assertThat(records).hasSize(events.size());

        for (Map.Entry<String, Event> entry : events.entrySet()) {
            String tripId = ((TripEvent) entry.getValue()).tripId().toString();
            ConsumerRecord<String, String> record = records.stream()
                    .filter(r -> r.key().equals(tripId)).findFirst().orElseThrow();
            assertThat(record.topic()).isEqualTo(TOPIC);

            ObjectNode payload = (ObjectNode) objectMapper.readTree(record.value());
            assertThat(payload.get("tripId").asString()).isEqualTo(tripId);
            assertThat(payload.get("aggregateId").asString()).isEqualTo(tripId);
            String fixtureTripId = EventJsonContractTest.TRIP_ID.toString();
            payload.put("tripId", fixtureTripId);
            payload.put("aggregateId", fixtureTripId);
            assertThat((Object) payload).as(entry.getKey())
                    .isEqualTo(objectMapper.readTree(Files.readString(
                            Path.of("src/test/resources/events", entry.getKey() + ".json"))));
        }
    }

    private List<ConsumerRecord<String, String>> poll(Set<UUID> tripIds, Duration duration) {
        Set<String> keys = new java.util.HashSet<>();
        tripIds.forEach(id -> keys.add(id.toString()));
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        Instant deadline = Instant.now().plus(duration);
        while (Instant.now().isBefore(deadline)) {
            consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                if (keys.contains(record.key())) {
                    matching.add(record);
                }
            });
        }
        return matching;
    }

    private List<ConsumerRecord<String, String>> pollUntil(Set<UUID> tripIds, int expected) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (matching.size() < expected && Instant.now().isBefore(deadline)) {
            matching.addAll(poll(tripIds, Duration.ofMillis(500)));
        }
        // Give a duplicate the chance to show up, so "delivered once" is really checked.
        matching.addAll(poll(tripIds, Duration.ofMillis(500)));
        return matching;
    }
}
