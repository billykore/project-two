package org.kore.myapp.core.port;

import org.kore.myapp.core.exception.EventPublishException;
import org.kore.myapp.core.valueobject.Event;

/**
 * Publishes domain {@link Event}s to the outside of the core.
 */
public interface EventPublisher {
    /**
     * Publishes an event.
     *
     * @param event the {@link Event} to publish.
     * @throws EventPublishException if the event fails to be published.
     */
    void publish(Event event);
}
