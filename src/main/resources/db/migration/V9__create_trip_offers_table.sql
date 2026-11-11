CREATE TABLE trip_offers
(
    id           BIGSERIAL PRIMARY KEY,
    trip_id      UUID        NOT NULL REFERENCES trips (id),
    driver_id    BIGINT      NOT NULL REFERENCES driver_profiles (id),

    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at   TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,                                    -- NULL while the offer is PENDING

    CONSTRAINT chk_trip_offers_status CHECK (status IN
                                             ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_trip_offers_responded_at CHECK (
        status NOT IN ('ACCEPTED', 'REJECTED') OR responded_at IS NOT NULL)
);

-- A driver can hold at most one pending offer per trip (backs TripOfferRepository#findPending).
CREATE UNIQUE INDEX uq_trip_offers_pending ON trip_offers (trip_id, driver_id)
    WHERE status = 'PENDING';

-- Lets a scheduler find pending offers that are about to expire.
CREATE INDEX idx_trip_offers_pending_expires ON trip_offers (expires_at)
    WHERE status = 'PENDING';
