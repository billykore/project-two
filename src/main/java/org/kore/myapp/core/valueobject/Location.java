package org.kore.myapp.core.valueobject;

/**
 * A place on the map, such as a trip pickup point or destination.
 *
 * @param coordinate geographic point of the place.
 * @param address    human-readable address, or null if not provided.
 */
public record Location(Coordinate coordinate, String address) {
    public Location(Coordinate coordinate) {
        this(coordinate, null);
    }
}
