package org.kore.myapp.adapter.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.kore.myapp.core.valueobject.Event;

/**
 * Jackson mix-in for {@link Event}: adds {@code aggregateId} to the message JSON. Keeping it here lets the domain
 * event stay free of JSON annotations; the transport decides the message shape.
 */
abstract class EventJsonMixin {

    @JsonProperty("aggregateId")
    abstract String aggregateId();
}
