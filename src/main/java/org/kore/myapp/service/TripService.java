package org.kore.myapp.service;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.port.EventPublisher;
import org.kore.myapp.core.port.IdGenerator;
import org.kore.myapp.core.port.ServiceTypeRepository;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.port.TripUseCase;
import org.kore.myapp.core.valueobject.CreateTripCommand;
import org.kore.myapp.core.valueobject.TripEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TripService implements TripUseCase {

    private final TripRepository tripRepository;

    private final ServiceTypeRepository serviceTypeRepository;

    private final EventPublisher eventPublisher;

    private final FareCalculator fareCalculator;

    private final Clock clock;

    private final IdGenerator ids;

    @Override
    @Transactional
    public Trip create(CreateTripCommand command) {
        ServiceType serviceType = serviceTypeRepository.findActiveByName(command.serviceTypeName())
                .orElseThrow(() -> new InvalidTripException("Unknown service type, expected MyRide or MyCar"));

        tripRepository.findActiveByCustomerId(command.customerId()).ifPresent(trip -> {
            throw new ActiveTripExistsException(command.customerId(), trip.getId());
        });

        Instant now = clock.instant();
        Trip saved = tripRepository.add(Trip.request(
                ids.newId(),
                command.customerId(),
                serviceType,
                command.pickup(),
                command.destination(),
                fareCalculator,
                now
        ));

        eventPublisher.publish(
                new TripEvent.Requested(
                        ids.newId(),
                        now,
                        saved.getId(),
                        saved.getCustomerId(),
                        saved.getServiceTypeId(),
                        command.pickup().coordinate()
                )
        );

        return saved;
    }
}
