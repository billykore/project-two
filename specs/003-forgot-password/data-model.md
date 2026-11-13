# Data Model: Forgot Password

Domain types live in `org.kore.myapp.core`. Persistence mapping annotations on entities are the one permitted
framework dependency (constitution VIII). All times are `Instant` from the injected `Clock`.

## Value objects (`core/valueobject`)

### ContactChannel (new, enum)

`EMAIL`, `PHONE`. Stored as text in `password_reset_codes.channel`.

### Contact (new, record)

`Contact(ContactChannel channel, String address)`: a normalized email address or phone number that identifies an
account and receives the code. It is built only by the reset commands, so it is always valid.

### PasswordRules (new, final class, static methods only)

The password rules extracted from `RegisterUserCommand` (research R4), with the messages unchanged:

| Method | Rule | Message |
|--------|------|---------|
| `formatError(String password)` | present | `is required` |
| | ≥ 8 code points | `must be at least 8 characters` |
| | ≤ 72 code points | `must be at most 72 characters` |
| | ≤ 72 UTF-8 bytes | `must be at most 72 bytes; non-ASCII characters count as more than one` |
| `usernameError(String password, String username)` | not equal to username | `must not be the same as the username` |

Each returns the message, or `null` when the rule holds. The class also holds the constant `HISTORY_SIZE = 3`
(FR-012: the current password plus the 2 before it).

### ContactRules (new, package-private final class, static methods only)

The email and phone format rules shared by registration and both reset commands (research R4):
- `emailFormatError` and `phoneFormatError` keep the registration messages;
- `normalizedEmail` strips and lowercases the email, and treats blank as absent;
- `phoneOrNull` treats a blank phone as absent;
- `checkContact` adds `provide either email or phone` on both fields unless exactly one is present;
- `contactOf` builds the `Contact`.

### FieldError (changed, record)

`FieldError(String field, String message)`. `field` was `RegistrationField`; it is now the client-facing property
name (research R5). Registration passes `RegistrationField.X.jsonName()`, so its responses are unchanged.

### RequestPasswordResetCommand (new, record)

`RequestPasswordResetCommand(String email, String phone)`. The constructor validates and normalizes:

- exactly one of `email` and `phone` is non-blank. Otherwise there are errors on both:
  `email`: `provide either email or phone`, and `phone`: the same message;
- `email`: stripped, lowercased, then the registration email rule (`must be a valid email address`);
- `phone`: the registration phone rule (`must be in international format, e.g. +6281234567890`).

It exposes `contact()`, which returns a `Contact`. On failure it throws `InvalidPasswordResetException` listing
every invalid field.

### ResetPasswordCommand (new, record)

`ResetPasswordCommand(String email, String phone, String code, String newPassword)`:

- `email` / `phone`: same rules as `RequestPasswordResetCommand`;
- `code`: exactly 6 ASCII digits (`[0-9]{6}`), leading zeros allowed. Otherwise `code`: `must be 6 digits`;
- `newPassword`: `PasswordRules.formatError`.

It exposes `contact()`. `toString()` masks `code` and `newPassword`. On failure it throws
`InvalidPasswordResetException` listing every invalid field.

### PasswordResetPolicy (new, record)

`PasswordResetPolicy(Duration codeTtl, int maxAttempts, Duration resendInterval, int maxCodesPerHour)`. It is built
in `config` from `PasswordResetProperties`; its constructor rejects non-positive values. Defaults: 5 min, 5, 60 s,
5.

## Entities (`core/entity`)

### User (changed)

New method:

- `changePassword(String newPasswordHash)`: replaces `password`. It throws `IllegalArgumentException` if the hash
  is null or blank.

No new columns. The existing nullable `email` and `phone` are the lookup keys for a reset.

### Session (changed mapping only)

`revokedAt` mapping: `nullable = false` → nullable, matching column `sessions.revoked_at` (V3). Revocation is a
bulk update in the adapter (research R12).

### PasswordResetCode (new)

One issued code.

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | identity |
| `userId` | `long` | owner account |
| `codeHash` | `String` | HMAC-SHA256 of the 6-digit code, 64 lowercase hex characters (research R3); never the code |
| `channel` | `ContactChannel` | where it was sent |
| `issuedAt` | `Instant` | |
| `expiresAt` | `Instant` | `issuedAt + policy.codeTtl` |
| `failedAttempts` | `int` | 0 … `maxAttempts` |
| `usedAt` | `Instant` | set once, on successful reset |
| `invalidatedAt` | `Instant` | set when superseded or exhausted |

Methods (all take `now` as an argument):

- `static issue(long userId, String codeHash, ContactChannel channel, Instant now, Duration ttl)`.
- `isUsable(Instant now)`: `usedAt == null && invalidatedAt == null && now < expiresAt`.
- `recordFailedAttempt(Instant now, int maxAttempts)`: increments `failedAttempts`; when it reaches `maxAttempts`,
  sets `invalidatedAt = now`. Throws `IllegalStateException` if the code is not usable.
- `markUsed(Instant now)`: sets `usedAt`. Throws `IllegalStateException` if the code is not usable.
- `invalidate(Instant now)`: sets `invalidatedAt` if the code is still open. Otherwise does nothing.

State transitions:

```text
            issue
              │
              ▼
  ┌──────► OPEN ──────── markUsed ──────────► USED
  │           │
  │  recordFailedAttempt (count < max)
  └───────────┘
              │ recordFailedAttempt (count = max) / invalidate (new code requested)
              ▼
         INVALIDATED

  OPEN with now ≥ expiresAt behaves as EXPIRED (not usable); it becomes INVALIDATED when the next code is issued.
```

### PasswordHistoryEntry (new)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | identity |
| `userId` | `long` | owner account |
| `passwordHash` | `String` | the hash that was current before a reset |
| `replacedAt` | `Instant` | when it stopped being current |

Factory: `static of(long userId, String passwordHash, Instant replacedAt)`.

## Exceptions (`core/exception`)

| Exception | When | HTTP |
|-----------|------|------|
| `InvalidPasswordResetException(List<FieldError>)` (new) | input breaks a rule; new password equals username or is one of the last 3 | 400 `errors[]` |
| `InvalidResetCodeException` (new) | no matching account, no usable code, or wrong code; fixed message `Invalid or expired code` | 400 |
| `CodeDeliveryException` (new) | a `ResetCodeSender` could not deliver; handled in the service, never reaches HTTP | — |
| `PasswordResetBusyException` (new) | the account lock was not acquired within the timeout, or a second open code was rejected by the index (research R6); handled as "no code issued" in `requestReset`, propagated from `resetPassword` | 503 + `Retry-After: 1` (confirm only) |

## Database: migration `V12__create_password_reset_tables.sql`

```text
password_reset_codes
  id               BIGSERIAL    PRIMARY KEY
  user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE
  code_hash        CHAR(64)     NOT NULL
  channel          VARCHAR(5)   NOT NULL  CHECK (channel IN ('EMAIL', 'PHONE'))
  issued_at        TIMESTAMPTZ  NOT NULL
  expires_at       TIMESTAMPTZ  NOT NULL  CHECK (expires_at > issued_at)
  failed_attempts  SMALLINT     NOT NULL DEFAULT 0  CHECK (failed_attempts >= 0)
  used_at          TIMESTAMPTZ
  invalidated_at   TIMESTAMPTZ
  CHECK (used_at IS NULL OR invalidated_at IS NULL)

  INDEX idx_password_reset_codes_user_issued ON (user_id, issued_at)
  UNIQUE INDEX uq_password_reset_codes_open ON (user_id) WHERE used_at IS NULL AND invalidated_at IS NULL

password_history
  id             BIGSERIAL    PRIMARY KEY
  user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE
  password_hash  VARCHAR(255) NOT NULL
  replaced_at    TIMESTAMPTZ  NOT NULL

  INDEX idx_password_history_user_replaced ON (user_id, replaced_at DESC)
```

No change to `users` or existing data. Accounts without email or phone simply never match a lookup.

## Rule → location map

| Rule (spec) | Enforced in |
|-------------|-------------|
| FR-002 contact format | `RequestPasswordResetCommand`, `ResetPasswordCommand` |
| FR-004 identical response | `PasswordResetService.requestReset` returns nothing; controller always `202` |
| FR-005 6 digits, 5 min, single use | `ResetCodeGenerator`, `PasswordResetCode.issue/markUsed/isUsable` |
| FR-006 newest code only | service invalidates the open code before issuing; partial unique index |
| FR-007 5 wrong attempts | `PasswordResetCode.recordFailedAttempt`; commit despite exception (research R7) |
| FR-008 60 s / 5 per hour | service, via `PasswordResetCodeRepository.countIssuedSince` under the account lock |
| FR-010 one error | `InvalidResetCodeException` for every code failure, including unknown account |
| FR-011 registration rules | `PasswordRules` (format in command, username in service) |
| FR-012 last 3 passwords | service compares with `users.password` + 2 newest history hashes |
| FR-013 refused password keeps code | password checks run after the code check, before `markUsed`; rollback leaves the code untouched |
| FR-015 end sessions | `SessionRepository.revokeAllForUser` |
| FR-016 hashed only | `ResetCodeHasher` (HMAC) for codes, `PasswordHasher` (BCrypt) for passwords; masked `toString`s; development sender is the only exception (FR-020) |
| FR-017 one acceptance | account row lock with a 5 s timeout, translated to `PasswordResetBusyException` (research R6) |
