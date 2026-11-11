package org.kore.myapp.common;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.config.FareProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FareCalculator {

    private final FareProperties props;

    /**
     * Calculate fare based on distance.
     * Returns minimum fare if calculated fare is less than minimum fare.
     */
    public long calculateFare(int distanceMeters) {
        if (distanceMeters <= 0) {
            throw new IllegalArgumentException("Distance must be positive");
        }
        return Math.max(distanceMeters * props.perMeter(), props.minimum());
    }
}
