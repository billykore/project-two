package org.kore.myapp.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kore.myapp.api.doc.ActiveTripProblemDoc;
import org.kore.myapp.api.doc.ApiDocs;
import org.kore.myapp.api.doc.ProblemDoc;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Customer trip booking.")
public class TripController {

    private final TripUseCase tripUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTrip", summary = "Customer books a trip", description = """
            Books a trip for the calling customer (the token's numeric `sub`). A customer can have at most one \
            unfinished trip (`REQUESTED`, `DRIVER_ASSIGNED`, `DRIVER_ARRIVED`, or `ON_TRIP`). The fare is computed \
            from the straight-line distance between pickup and destination.""")
    @ApiResponse(responseCode = "201", description = "Trip created.",
            headers = @Header(name = "Location", description = "Path of the new trip, `/trips/{id}`.",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = """
            Validation failed, `serviceType` is not exactly `MyRide` or `MyCar`, pickup and destination are the \
            same place, or the body is malformed.""",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDoc.class),
                    examples = {@ExampleObject(name = "invalidTrip", value = ApiDocs.INVALID_TRIP),
                            @ExampleObject(name = "malformed", ref = ApiDocs.MALFORMED_REQUEST)}))
    @ApiResponse(responseCode = "401", ref = ApiDocs.UNAUTHENTICATED)
    @ApiResponse(responseCode = "409", description = """
            The customer still has an unfinished trip. `activeTripId` is included, except when the other trip was \
            created by a concurrent request.""",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON,
                    schema = @Schema(implementation = ActiveTripProblemDoc.class),
                    examples = @ExampleObject(name = "activeTrip", value = ApiDocs.ACTIVE_TRIP)))
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
