package org.kore.myapp.api.controller;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.port.TripUseCase;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.CreateTripCommand;
import org.kore.myapp.core.valueobject.Location;
import org.kore.myapp.core.valueobject.PaymentMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, ProblemDetailWriter.class})
class TripControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String BODY = """
            {
              "serviceType": "MyRide",
              "pickup":      {"latitude": -6.1754, "longitude": 106.8272, "address": "Monas"},
              "destination": {"latitude": -6.2183, "longitude": 106.8022, "address": "Senayan"},
              "paymentMethod": "QRIS"
            }""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TripUseCase tripUseCase;

    @Test
    void createReturnsCreatedTripWithLocation() throws Exception {
        when(tripUseCase.create(any())).thenReturn(requestedTrip());

        mvc.perform(createTrip(BODY).with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/trips/" + TRIP_ID))
                .andExpect(jsonPath("$.id").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.serviceType").value(1));
        verify(tripUseCase).create(new CreateTripCommand(42, "MyRide",
                new Location(new Coordinate(-6.1754, 106.8272), "Monas"),
                new Location(new Coordinate(-6.2183, 106.8022), "Senayan"),
                PaymentMethod.QRIS));
    }

    @Test
    void invalidBodyIsABadRequest() throws Exception {
        mvc.perform(createTrip(BODY.replace("\"paymentMethod\": \"QRIS\"", "\"paymentMethod\": null"))
                        .with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isBadRequest());
        verify(tripUseCase, never()).create(any());
    }

    @Test
    void invalidTripIsABadRequestProblem() throws Exception {
        when(tripUseCase.create(any()))
                .thenThrow(new InvalidTripException("Unknown service type, expected MyRide or MyCar"));

        mvc.perform(createTrip(BODY).with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid trip"))
                .andExpect(jsonPath("$.detail").value("Unknown service type, expected MyRide or MyCar"));
    }

    @Test
    void activeTripIsAConflictProblemWithTheActiveTripId() throws Exception {
        when(tripUseCase.create(any())).thenThrow(new ActiveTripExistsException(42, TRIP_ID));

        mvc.perform(createTrip(BODY).with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Active trip exists"))
                .andExpect(jsonPath("$.activeTripId").value(TRIP_ID.toString()));
    }

    @Test
    void missingTokenIsUnauthenticated() throws Exception {
        mvc.perform(createTrip(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
    }

    /** Regression test: a token without a subject used to book a trip for customer 0. */
    @Test
    void tokenWithoutSubjectIsUnauthenticated() throws Exception {
        mvc.perform(createTrip(BODY).with(jwt().jwt(j -> j.claims(claims -> claims.remove("sub")))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
        verify(tripUseCase, never()).create(any());
    }

    /** Regression test: a non-numeric subject used to fail with a 500. */
    @Test
    void tokenWithNonNumericSubjectIsUnauthenticated() throws Exception {
        mvc.perform(createTrip(BODY).with(jwt().jwt(j -> j.subject("abc"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
        verify(tripUseCase, never()).create(any());
    }

    private static MockHttpServletRequestBuilder createTrip(String body) {
        return post("/trips").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static Trip requestedTrip() {
        ServiceType myRide = ServiceType.builder().id(1L).name("MyRide").build();
        return Trip.request(TRIP_ID, 42, myRide,
                new Location(new Coordinate(-6.1754, 106.8272), "Monas"),
                new Location(new Coordinate(-6.2183, 106.8022), "Senayan"),
                new FareCalculator(30, 10_000), Instant.parse("2026-11-11T08:00:00Z"));
    }
}
