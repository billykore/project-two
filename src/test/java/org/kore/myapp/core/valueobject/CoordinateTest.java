package org.kore.myapp.core.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CoordinateTest {

    @Test
    void oneDegreeOfLongitudeOnTheEquatorIsAbout111Km() {
        Coordinate a = new Coordinate(0, 0);
        Coordinate b = new Coordinate(0, 1);

        assertThat(a.distanceMetersTo(b)).isEqualTo(111_195);
    }

    @Test
    void distanceIsSymmetric() {
        Coordinate monas = new Coordinate(-6.1754, 106.8272);
        Coordinate senayan = new Coordinate(-6.2183, 106.8022);

        assertThat(monas.distanceMetersTo(senayan)).isEqualTo(senayan.distanceMetersTo(monas));
        // About 5.5 km in a straight line.
        assertThat(monas.distanceMetersTo(senayan)).isCloseTo(5_500, within(200));
    }

    @Test
    void distanceToSamePointIsZero() {
        Coordinate a = new Coordinate(-6.2088, 106.8456);

        assertThat(a.distanceMetersTo(a)).isZero();
    }
}
