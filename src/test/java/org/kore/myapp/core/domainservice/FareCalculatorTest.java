package org.kore.myapp.core.domainservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareCalculatorTest {

    private final FareCalculator fares = new FareCalculator(30, 10_000);

    @Test
    void longTripsPayDistanceTimesRate() {
        assertThat(fares.calculateFare(5_000)).isEqualTo(150_000);
    }

    @Test
    void shortTripsPayTheMinimumFare() {
        assertThat(fares.calculateFare(100)).isEqualTo(10_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveDistance(int distance) {
        assertThatThrownBy(() -> fares.calculateFare(distance)).isInstanceOf(IllegalArgumentException.class);
    }
}
