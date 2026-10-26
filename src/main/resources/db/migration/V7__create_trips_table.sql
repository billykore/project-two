CREATE TABLE trips
(
    id                  UUID PRIMARY KEY          DEFAULT gen_random_uuid(),

    customer_id         BIGINT           NOT NULL REFERENCES users (id),
    driver_id           BIGINT REFERENCES driver_profiles (id), -- NULL until finds a driver
    service_type_id     BIGINT           NOT NULL REFERENCES service_types (id),

    status              VARCHAR(20)      NOT NULL DEFAULT 'REQUESTED',

    pickup_lat          DOUBLE PRECISION NOT NULL,
    pickup_lng          DOUBLE PRECISION NOT NULL,
    pickup_address      VARCHAR(255),
    destination_lat     DOUBLE PRECISION NOT NULL,
    destination_lng     DOUBLE PRECISION NOT NULL,
    destination_address VARCHAR(255),

    distance_meters     INTEGER          NOT NULL,
    fare                BIGINT           NOT NULL,              -- fare snapshot when requested

    cancelled_by        VARCHAR(20),
    cancel_reason       VARCHAR(255),

    requested_at        TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    driver_assigned_at  TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,

    version             BIGINT           NOT NULL DEFAULT 0,    -- optimistic locking (@Version)
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_trips_status CHECK (status IN
                                       ('REQUESTED', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVED', 'ON_TRIP', 'COMPLETED',
                                        'CANCELLED')),
    CONSTRAINT chk_trips_fare CHECK (fare >= 0),
    CONSTRAINT chk_trips_distance CHECK (distance_meters >= 0),
    CONSTRAINT chk_trips_pickup_coord CHECK (
        pickup_lat BETWEEN -90 AND 90 AND pickup_lng BETWEEN -180 AND 180),
    CONSTRAINT chk_trips_destination_coord CHECK (
        destination_lat BETWEEN -90 AND 90 AND destination_lng BETWEEN -180 AND 180),
    CONSTRAINT chk_trips_cancelled_by CHECK (
        cancelled_by IS NULL OR cancelled_by IN ('CUSTOMER', 'DRIVER', 'SYSTEM')),

    CONSTRAINT chk_trips_driver_required CHECK (
        status IN ('REQUESTED', 'CANCELLED') OR driver_id IS NOT NULL),
    CONSTRAINT chk_trips_cancelled_at CHECK (
        (status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE INDEX idx_trips_customer_created ON trips (customer_id, created_at DESC);

CREATE INDEX idx_trips_driver_status ON trips (driver_id, status);

CREATE INDEX idx_trips_requested ON trips (service_type_id, requested_at)
    WHERE status = 'REQUESTED';