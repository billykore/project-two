-- A customer can have at most one unfinished trip. TripService checks this first, but two concurrent requests can
-- both pass that check; this index makes the database reject the second one.
-- Keep the statuses in sync with TripStatus#activeStatuses.
CREATE UNIQUE INDEX uq_trips_customer_active ON trips (customer_id)
    WHERE status IN ('REQUESTED', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVED', 'ON_TRIP');
