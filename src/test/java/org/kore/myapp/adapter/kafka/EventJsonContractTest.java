package org.kore.myapp.adapter.kafka;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kore.myapp.core.valueobject.CancelledBy;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Event;
import org.kore.myapp.core.valueobject.Money;
import org.kore.myapp.core.valueobject.TripEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON payload of every trip event to a golden fixture in {@code src/test/resources/events/}
 * (contracts/events.md). The fixtures were captured from the application's own auto-configured mapper before the
 * hexagonal refactor, when the domain event still carried its JSON annotation, so any change to an event's JSON fields
 * fails here.
 * <p>
 * To (re)capture the fixtures deliberately, run with {@code -Devents.fixtures.write=true}. Never do that to make a
 * failing comparison pass: a failure means the event contract changed.
 */
@JsonTest
class EventJsonContractTest {

    static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final Instant OCCURRED_AT = Instant.parse("2026-11-11T08:00:00Z");
    static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static final Path FIXTURES = Path.of("src/test/resources/events");

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<Arguments> events() {
        return Stream.of(
                Arguments.of("requested", new TripEvent.Requested(
                        EVENT_ID, OCCURRED_AT, TRIP_ID, 42, 1, new Coordinate(-6.1754, 106.8272))),
                Arguments.of("driver-assigned", new TripEvent.DriverAssigned(EVENT_ID, OCCURRED_AT, TRIP_ID, 7)),
                Arguments.of("rejected", new TripEvent.Rejected(EVENT_ID, OCCURRED_AT, TRIP_ID)),
                Arguments.of("completed", new TripEvent.Completed(EVENT_ID, OCCURRED_AT, TRIP_ID, new Money(25_000))),
                Arguments.of("cancelled", new TripEvent.Cancelled(EVENT_ID, OCCURRED_AT, TRIP_ID, CancelledBy.CUSTOMER))
        );
    }

    /**
     * The message JSON is decided by the Kafka adapter (application mapper plus {@link EventJsonMixin}). Before the
     * refactor the domain event carried {@code @JsonProperty} itself; the fixtures were captured from that, so this
     * proves the move left every payload unchanged.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    void kafkaSenderMatchesFixture(String name, Event event) throws IOException {
        KafkaEventSender sender = new KafkaEventSender(null, objectMapper, null);

        assertMatchesFixture(name, sender.serialize(event));
    }

    void assertMatchesFixture(String name, String json) throws IOException {
        Path fixture = FIXTURES.resolve(name + ".json");
        if (Boolean.getBoolean("events.fixtures.write")) {
            Files.createDirectories(FIXTURES);
            Files.writeString(fixture, objectMapper.readTree(json).toPrettyString() + System.lineSeparator());
        }
        JsonNode expected = objectMapper.readTree(Files.readString(fixture));
        assertThat(objectMapper.readTree(json)).isEqualTo(expected);
    }
}
