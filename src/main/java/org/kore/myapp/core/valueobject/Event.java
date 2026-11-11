package org.kore.myapp.core.valueobject;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in the domain and is published through an {@link org.kore.myapp.core.port.EventPublisher}.
 */
public interface Event {
    /** Unique id of this event. */
    UUID eventId();

    /** Time when the event occurred. */
    Instant occurredAt();

    /** Id of the aggregate this event belongs to. */
    String aggregateId();
}
