package org.kore.myapp.adapter.kafka;

import lombok.extern.slf4j.Slf4j;
import org.kore.myapp.core.exception.EventPublishException;
import org.kore.myapp.core.port.EventPublisher;
import org.kore.myapp.core.valueobject.Event;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link EventPublisher} that delivers events to Kafka only after the transaction that raised them commits, so no
 * event describes a change that was rolled back.
 * <p>
 * Once the change is committed, a delivery failure is logged rather than thrown: the caller's change already
 * succeeded and must not be reported as failed. Outside a transaction the event is sent immediately.
 */
@Slf4j
@Component
public class AfterCommitEventPublisher implements EventPublisher {

    private final KafkaEventSender sender;

    AfterCommitEventPublisher(KafkaEventSender sender) {
        this.sender = sender;
    }

    @Override
    public void publish(Event event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.send(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sender.send(event);
                } catch (EventPublishException e) {
                    log.error("Failed to publish {} for aggregate {}",
                            event.getClass().getSimpleName(), event.aggregateId(), e);
                }
            }
        });
    }
}
