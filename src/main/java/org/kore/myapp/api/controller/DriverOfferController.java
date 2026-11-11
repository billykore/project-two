package org.kore.myapp.api.controller;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.response.TripResponse;
import org.kore.myapp.api.security.AuthenticatedCaller;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.port.TripOfferUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RestController
@RequestMapping("/driver/offers")
@RequiredArgsConstructor
class DriverOfferController {

    private final TripOfferUseCase offerUseCase;

    @PostMapping("/{tripId}/accept")
    public TripResponse accept(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        Trip trip = offerUseCase.accept(tripId, AuthenticatedCaller.userId(jwt));
        return TripResponse.from(trip);
    }
}
