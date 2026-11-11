package org.kore.myapp.api.controller;

import org.junit.jupiter.api.Test;
import org.kore.myapp.api.handler.JsonAuthenticationEntryPoint;
import org.kore.myapp.api.handler.ProblemDetailWriter;
import org.kore.myapp.config.SecurityConfig;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.entity.DriverProfile;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.port.TripOfferUseCase;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverOfferController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, ProblemDetailWriter.class})
class DriverOfferControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TripOfferUseCase offerUseCase;

    @Test
    void acceptReturnsTheAssignedTrip() throws Exception {
        Trip trip = Trip.request(TRIP_ID, 42, ServiceType.builder().id(1L).build(),
                new Location(new Coordinate(-6.1754, 106.8272)), new Location(new Coordinate(-6.2183, 106.8022)),
                new FareCalculator(30, 10_000), NOW);
        trip.assignDriver(DriverProfile.builder().id(7L).build(), NOW);
        when(offerUseCase.accept(TRIP_ID, 70)).thenReturn(trip);

        mvc.perform(post("/driver/offers/{tripId}/accept", TRIP_ID).with(jwt().jwt(j -> j.subject("70"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.status").value("DRIVER_ASSIGNED"));
    }

    @Test
    void tripNoLongerWaitingIsAConflict() throws Exception {
        when(offerUseCase.accept(TRIP_ID, 70)).thenThrow(new IllegalStateException("already assigned"));

        mvc.perform(post("/driver/offers/{tripId}/accept", TRIP_ID).with(jwt().jwt(j -> j.subject("70"))))
                .andExpect(status().isConflict());
    }

    /** Regression test: a token without a subject used to fail with a 500. */
    @Test
    void tokenWithoutSubjectIsUnauthenticated() throws Exception {
        mvc.perform(post("/driver/offers/{tripId}/accept", TRIP_ID)
                        .with(jwt().jwt(j -> j.claims(claims -> claims.remove("sub")))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
        verify(offerUseCase, never()).accept(any(), anyLong());
    }

    /** Regression test: a non-numeric subject used to fail with a 500. */
    @Test
    void tokenWithNonNumericSubjectIsUnauthenticated() throws Exception {
        mvc.perform(post("/driver/offers/{tripId}/accept", TRIP_ID).with(jwt().jwt(j -> j.subject("abc"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
        verify(offerUseCase, never()).accept(any(), anyLong());
    }
}
