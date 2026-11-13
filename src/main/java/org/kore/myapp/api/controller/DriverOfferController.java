package org.kore.myapp.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.doc.ApiDocs;
import org.kore.myapp.api.doc.ProblemDoc;
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
@Tag(name = "Driver", description = "Driver actions on trip offers.")
class DriverOfferController {

    private final TripOfferUseCase offerUseCase;

    @PostMapping("/{tripId}/accept")
    @Operation(operationId = "acceptOffer", summary = "Driver accepts a trip offer", description = """
            The calling driver (the token's numeric `sub`) accepts the pending offer for the trip. The trip moves \
            to `DRIVER_ASSIGNED` and the driver's availability to `ON_TRIP`.

            **Not yet defined:** when the caller has no driver profile, has no pending offer for the trip, or the \
            trip does not exist, no exception handler maps the error, so the response is not a defined Problem \
            Details answer yet.""")
    @ApiResponse(responseCode = "200", description = "Offer accepted; the trip is now `DRIVER_ASSIGNED`.")
    @ApiResponse(responseCode = "401", ref = ApiDocs.UNAUTHENTICATED)
    @ApiResponse(responseCode = "409",
            description = "The trip is no longer `REQUESTED` (for example cancelled, or another driver took it).",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDoc.class)))
    public TripResponse accept(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        Trip trip = offerUseCase.accept(tripId, AuthenticatedCaller.userId(jwt));
        return TripResponse.from(trip);
    }
}
