package org.kore.myapp.core.valueobject;

/**
 * A geographic point on Earth.
 *
 * @param latitude  latitude in degrees, from -90.0 to 90.0.
 * @param longitude longitude in degrees, from -180.0 to 180.0.
 * @throws IllegalArgumentException if {@code latitude} or {@code longitude} is out of range.
 */
public record Coordinate(double latitude, double longitude) {

    /** Mean radius of the Earth in meters. */
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    public Coordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude out of range");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude out of range");
        }
    }

    /**
     * Straight-line (great-circle) distance to another coordinate, using the haversine formula.
     *
     * <p>This is shorter than the actual road distance, which needs a routing service.
     *
     * @param other the other coordinate.
     * @return distance in meters, rounded to the nearest meter.
     */
    public int distanceMetersTo(Coordinate other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(other.longitude - longitude);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLng / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }
}
