ALTER TABLE sessions
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX uq_sessions_public_id ON sessions (public_id);