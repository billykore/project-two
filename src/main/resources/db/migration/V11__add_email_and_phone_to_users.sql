-- Contact details collected at registration. Nullable because accounts created before registration existed have
-- neither; unique indexes ignore NULLs, so those accounts do not collide. New registrations always set both.
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN phone VARCHAR(16);

-- Emails are stored lowercase so the plain unique index compares them case-insensitively.
ALTER TABLE users ADD CONSTRAINT chk_users_email_lowercase CHECK (email = lower(email));

CREATE UNIQUE INDEX uq_users_email ON users (email);
CREATE UNIQUE INDEX uq_users_phone ON users (phone);
