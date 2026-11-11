package org.kore.myapp.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.common.FareCalculator;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.port.TripUseCase;
import org.kore.myapp.core.valueobject.TripEvent;
import org.kore.myapp.core.valueobject.TripStatus;
import org.kore.myapp.service.command.CreateTripCommand;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripService implements TripUseCase {

    private final TripRepository tripRepository;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final FareCalculator fareCalculator;

    @Override
    @Transactional
    public Trip create(CreateTripCommand command) {
        tripRepository.findActiveByCustomerId(command.customerId()).ifPresent(trip -> {
            throw new ActiveTripExistsException(command.customerId(), trip.getId());
        });

        int distanceMeters = command.distanceMeters();
        if (distanceMeters <= 0) {
            throw new InvalidTripException("Pickup and destination must be different places");
        }

        Trip saved = tripRepository.saveAndFlush(
                Trip.builder()
                        .customerId(command.customerId())
                        .serviceTypeId(command.serviceTypeId().ordinal())
                        .status(TripStatus.REQUESTED)
                        .pickupLat(command.pickup().coordinate().latitude())
                        .pickupLng(command.pickup().coordinate().longitude())
                        .pickupAddress(command.pickup().address())
                        .destinationLat(command.destination().coordinate().latitude())
                        .destinationLng(command.destination().coordinate().longitude())
                        .destinationAddress(command.destination().address())
                        .distanceMeters(distanceMeters)
                        .fare(fareCalculator.calculateFare(distanceMeters))
                        .requestedAt(Instant.now())
                        .build()
        );

        applicationEventPublisher.publishEvent(
                new TripEvent.Requested(
                        UUID.randomUUID(),
                        Instant.now(),
                        Objects.requireNonNull(saved.getId(), "Trip must be persisted"),
                        saved.getCustomerId(),
                        saved.getServiceTypeId(),
                        command.pickup().coordinate()
                )
        );

        return saved;
    }
}
