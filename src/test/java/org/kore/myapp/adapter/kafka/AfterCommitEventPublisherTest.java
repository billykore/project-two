package org.kore.myapp.adapter.kafka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.exception.EventPublishException;
import org.kore.myapp.core.valueobject.TripEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AfterCommitEventPublisherTest {

    private static final TripEvent EVENT = new TripEvent.Rejected(
            UUID.randomUUID(), Instant.parse("2026-11-11T08:00:00Z"), UUID.randomUUID());

    private KafkaEventSender sender;
    private AfterCommitEventPublisher publisher;

    @BeforeEach
    void setUp() {
        sender = mock(KafkaEventSender.class);
        publisher = new AfterCommitEventPublisher(sender);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void insideATransactionTheEventIsSentOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(EVENT);
        verify(sender, never()).send(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(sender).send(EVENT);
    }

    @Test
    void aRolledBackTransactionNeverSendsTheEvent() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(EVENT);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(sender, never()).send(any());
    }

    @Test
    void aFailureAfterCommitIsLoggedAndNotThrown() {
        doThrow(new EventPublishException("broker down", null)).when(sender).send(EVENT);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(EVENT);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verify(sender).send(EVENT);
    }

    @Test
    void outsideATransactionTheEventIsSentImmediatelyAndFailuresPropagate() {
        doThrow(new EventPublishException("broker down", null)).when(sender).send(EVENT);

        assertThatThrownBy(() -> publisher.publish(EVENT)).isInstanceOf(EventPublishException.class);
        verify(sender).send(EVENT);
    }
}
