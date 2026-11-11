package org.kore.myapp.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.request.CreateTripRequest;
import org.kore.myapp.api.response.TripResponse;
import org.kore.myapp.api.security.AuthenticatedCaller;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.port.TripUseCase;
import org.kore.myapp.core.valueobject.CreateTripCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripUseCase tripUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TripResponse> handleCreate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTripRequest request
    ) {
        Trip trip = tripUseCase.create(
                new CreateTripCommand(
                        AuthenticatedCaller.userId(jwt),
                        request.serviceType(),
                        request.pickup().toLocation(),
                        request.destination().toLocation(),
                        request.paymentMethod()
                )
        );
        return ResponseEntity
                .created(URI.create("/trips/" + trip.getId()))
                .body(TripResponse.from(trip));
    }
}
