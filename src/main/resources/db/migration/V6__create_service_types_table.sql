CREATE TABLE service_types
(
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(30) NOT NULL,
    name           VARCHAR(50) NOT NULL,
    vehicle_type   VARCHAR(20) NOT NULL,
    max_passengers INTEGER     NOT NULL,
    display_order  INTEGER     NOT NULL DEFAULT 0,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_service_types_code UNIQUE (code),
    CONSTRAINT chk_service_types_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT chk_service_types_vehicle_type CHECK (vehicle_type IN ('MOTORCYCLE', 'CAR')),
    CONSTRAINT chk_service_types_max_passengers CHECK (max_passengers BETWEEN 1 AND 10)
);

INSERT INTO service_types (code, name, vehicle_type, max_passengers, display_order)
VALUES ('MYRIDE', 'MyRide', 'MOTORCYCLE', 1, 1),
       ('MYCAR', 'MyCar', 'CAR', 4, 2);