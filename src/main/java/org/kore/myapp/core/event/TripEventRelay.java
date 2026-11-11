package org.kore.myapp.core.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kore.myapp.core.exception.EventPublishException;
import org.kore.myapp.core.port.EventPublisher;
import org.kore.myapp.core.valueobject.TripEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays {@link TripEvent}s to the {@link EventPublisher} only after the transaction that raised them has committed,
 * so no event is published for a trip that failed to be saved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventRelay {

    private final EventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(TripEvent event) {
        try {
            eventPublisher.publish(event);
        } catch (EventPublishException e) {
            // The trip is already committed, so a publishing failure must not fail the request.
            log.error("Failed to publish {} for aggregate {}", event.getClass().getSimpleName(), event.aggregateId(), e);
        }
    }
}
