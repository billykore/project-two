# Data Model: User Registration

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

## User (entity, `core/entity/User.java`, changed)

| Field       | Type            | Column               | Rules                                                              |
|-------------|-----------------|----------------------|--------------------------------------------------------------------|
| `id`        | `Long`          | `id BIGSERIAL PK`    | assigned by the database                                           |
| `username`  | `String`        | `username VARCHAR(150) UNIQUE` (`users_username_key`) | 3–50 chars, `[a-z0-9._-]`; unique            |
| `password`  | `String`        | `password VARCHAR(255)` | BCrypt hash only, never the plain password                      |
| `name`      | `String`        | `name VARCHAR(100)`  | trimmed, 1–100 chars                                               |
| `email`     | `String` **new**| `email VARCHAR(254)` nullable, `uq_users_email`, `chk_users_email_lowercase` | lowercase, valid form; unique; `null` only for accounts created before this feature |
| `phone`     | `String` **new**| `phone VARCHAR(16)` nullable, `uq_users_phone` | `+` and 8–15 digits; unique; `null` only for pre-existing accounts |
| `createdAt` | `LocalDateTime` | `created_at TIMESTAMP DEFAULT NOW()` | set by the database (research R7)                       |

**New factory**: `static User register(RegisterUserCommand command, String passwordHash)` builds an unsaved user
from an already-validated command and a hash. It rejects a blank hash with `IllegalArgumentException`, a
programming error. The Lombok builder stays for persistence fixtures and existing callers.

**Role**: there is no role column. A user is a *customer* unless a `driver_profiles` row with `APPROVED` status
exists, and registration never creates one (FR-013).

**Lifecycle**: none. A user is created once; this feature adds no updates or deletion.

## RegisterUserCommand (value object, `core/valueobject`, new)

`record RegisterUserCommand(String username, String password, String name, String email, String phone)`

The constructor validates **all** fields, collects every violation, and throws
`InvalidRegistrationException(List<FieldError>)` if any exist (research R1). Valid components are stored
normalized.

| Field      | Normalization        | Violations (field → message)                                                     |
|------------|----------------------|----------------------------------------------------------------------------------|
| `username` | none                 | null → "is required"; not `^[a-z0-9._-]{3,50}$` → "must be 3–50 characters: lowercase letters, digits, '.', '_' or '-'" |
| `password` | none (never trimmed) | null → "is required"; < 8 code points → "must be at least 8 characters"; > 72 code points → "must be at most 72 characters"; > 72 UTF-8 bytes → "must be at most 72 bytes; non-ASCII characters count as more than one"; equals `username` → "must not be the same as the username" |
| `name`     | `strip()`            | null or empty after strip → "is required"; > 100 chars → "must be at most 100 characters" |
| `email`    | `strip()`, then `toLowerCase(Locale.ROOT)` | null/blank → "is required"; > 254 chars or not the research R5 form → "must be a valid email address" |
| `phone`    | none                 | null/blank → "is required"; not `^\+[0-9]{8,15}$` → "must be in international format, e.g. +6281234567890" |

At most one message per field (the first that applies, in table order), so each field appears in `errors` once.
The password-equals-username check compares against the username as given, and only when both are otherwise
valid.

`toString()` is overridden to mask the password (`password=***`), so the command can't leak it into a log or an
exception message.

## RegistrationField (enum, `core/valueobject`, new)

`USERNAME, PASSWORD, NAME, EMAIL, PHONE`, each with `jsonName()` (`"username"`, `"password"`, `"name"`,
`"email"`, `"phone"`). The enum keeps field identity typed in the domain; the API handler uses `jsonName()` for
the response.

## FieldError (value object, `core/valueobject`, new)

`record FieldError(RegistrationField field, String message)`

## InvalidRegistrationException (`core/exception`, new)

- Extends `RuntimeException`.
- `List<FieldError> getErrors()` is unmodifiable and in field order.
- The message joins `"<jsonName>: <message>"` with `"; "`.

## RegistrationConflictException (`core/exception`, new)

- Extends `RuntimeException`.
- `Set<RegistrationField> getFields()` is a non-empty, unmodifiable `EnumSet` copy.
- The message names each field, for example `"username already taken; email already registered"`, with the
  wording from FR-003, FR-017, and FR-019:
  - USERNAME → "username already taken"
  - EMAIL → "email already registered"
  - PHONE → "phone number already registered"

## Migration V11 (`src/main/resources/db/migration/V11__add_email_and_phone_to_users.sql`, new)

```sql
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN phone VARCHAR(16);
ALTER TABLE users ADD CONSTRAINT chk_users_email_lowercase CHECK (email = lower(email));
CREATE UNIQUE INDEX uq_users_email ON users (email);
CREATE UNIQUE INDEX uq_users_phone ON users (phone);
```

Nullable columns allow existing accounts; unique indexes ignore `NULL`s (research R9). The constraint names are
referenced by `JpaUserRepository` (research R4) and asserted in its IT.

## Relationships

Unchanged: `sessions.user_id → users.id`, `driver_profiles.user_id → users.id`, `trips.customer_id → users.id`.
