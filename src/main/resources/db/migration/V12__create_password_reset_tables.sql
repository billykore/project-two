-- One row per one-time password-reset code that was issued. Rows stay after use so the per-hour limit can count them.
-- code_hash is the HMAC-SHA256 of the code as 64 hex characters; the code itself is never stored.
CREATE TABLE password_reset_codes
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash       CHAR(64)    NOT NULL,
    channel         VARCHAR(5)  NOT NULL CHECK (channel IN ('EMAIL', 'PHONE')),
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL CHECK (expires_at > issued_at),
    failed_attempts SMALLINT    NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    used_at         TIMESTAMPTZ,
    invalidated_at  TIMESTAMPTZ,
    CHECK (used_at IS NULL OR invalidated_at IS NULL)
);

CREATE INDEX idx_password_reset_codes_user_issued ON password_reset_codes (user_id, issued_at);

-- At most one open (neither used nor invalidated) code per account.
CREATE UNIQUE INDEX uq_password_reset_codes_open ON password_reset_codes (user_id)
    WHERE used_at IS NULL AND invalidated_at IS NULL;

-- Passwords an account had before a reset, so a new password can be compared with its latest ones.
CREATE TABLE password_history
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    replaced_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_password_history_user_replaced ON password_history (user_id, replaced_at DESC);
