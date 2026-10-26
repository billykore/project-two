CREATE TABLE driver_profiles
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    license_number      VARCHAR(30) NOT NULL,
    license_expires_at  DATE        NOT NULL,

    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    availability_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    approved_at         TIMESTAMPTZ,

    version             BIGINT      NOT NULL DEFAULT 0, -- optimistic locking (@Version)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_driver_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_driver_profiles_user UNIQUE (user_id),
    CONSTRAINT uq_driver_profiles_license UNIQUE (license_number),

    CONSTRAINT chk_driver_profiles_verification CHECK (
        verification_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT chk_driver_profiles_availability CHECK (
        availability_status IN ('OFFLINE', 'ONLINE', 'ON_TRIP')),

    CONSTRAINT chk_driver_profiles_online_requires_approval CHECK (
        availability_status = 'OFFLINE' OR verification_status = 'APPROVED')
);

CREATE INDEX idx_driver_profiles_online ON driver_profiles (id)
    WHERE availability_status = 'ONLINE';

CREATE INDEX idx_driver_profiles_pending ON driver_profiles (created_at)
    WHERE verification_status = 'PENDING';