package org.kore.myapp.core.domainservice;

/**
 * Prices a trip from its straight-line distance: a per-meter rate with a minimum fare.
 * <p>
 * A plain domain service built from values, so it can be used and tested without a framework.
 */
public final class FareCalculator {

    private final long perMeter;
    private final long minimum;

    /**
     * @param perMeter fare per meter of distance.
     * @param minimum  lowest fare charged for any trip.
     */
    public FareCalculator(long perMeter, long minimum) {
        this.perMeter = perMeter;
        this.minimum = minimum;
    }

    /**
     * Calculates the fare for a distance; returns the minimum fare if the distance-based fare is lower.
     *
     * @param distanceMeters straight-line distance of the trip, in meters.
     * @return the fare.
     * @throws IllegalArgumentException if the distance is not positive.
     */
    public long calculateFare(int distanceMeters) {
        if (distanceMeters <= 0) {
            throw new IllegalArgumentException("Distance must be positive");
        }
        return Math.max(distanceMeters * perMeter, minimum);
    }
}
