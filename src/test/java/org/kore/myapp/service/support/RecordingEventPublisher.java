package org.kore.myapp.service.support;

import org.kore.myapp.core.port.EventPublisher;
import org.kore.myapp.core.valueobject.Event;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-memory {@link EventPublisher} for application-service unit tests: records every published event.
 */
public class RecordingEventPublisher implements EventPublisher {

    private final List<Event> events = new ArrayList<>();

    @Override
    public void publish(Event event) {
        events.add(event);
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    /**
     * Asserts that exactly one event of the given type was published and returns it.
     */
    public <T extends Event> T single(Class<T> type) {
        List<T> matching = events.stream().filter(type::isInstance).map(type::cast).toList();
        assertThat(matching).as("published %s events", type.getSimpleName()).hasSize(1);
        return matching.get(0);
    }
}
