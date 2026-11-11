CREATE TABLE trip_status_history
(
    id          BIGSERIAL PRIMARY KEY,
    trip_id     UUID        NOT NULL REFERENCES trips (id),
    status      VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_status_history_trip ON trip_status_history (trip_id, occurred_at);