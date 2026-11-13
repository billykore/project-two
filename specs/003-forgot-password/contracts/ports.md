# Port Contracts: Forgot Password

All ports are in `org.kore.myapp.core.port`. They are plain interfaces with no framework types, annotations, or
default methods (constitution VI).

## PasswordResetUseCase (inbound, new)

```java
public interface PasswordResetUseCase {

    /**
     * Sends a one-time code to the contact if it belongs to an account and no limit applies. Completes normally in
     * every case, so callers cannot tell whether an account exists.
     *
     * @param command validated, normalized contact.
     */
    void requestReset(RequestPasswordResetCommand command);

    /**
     * Replaces the account's password using a code sent to it, uses up the code, and ends all of its sessions.
     *
     * @param command validated contact, code, and new password.
     * @throws InvalidResetCodeException      if no account matches, it has no usable code, or the code is wrong;
     *                                        a wrong code is counted even though this is thrown.
     * @throws InvalidPasswordResetException  if the new password equals the username or is one of the last 3
     *                                        passwords; the code stays usable.
     * @throws PasswordResetBusyException     if another reset of the same account holds it longer than the lock
     *                                        timeout; nothing was changed, and the caller may retry.
     */
    void resetPassword(ResetPasswordCommand command);
}
```

`InvalidPasswordResetException` for input rules is thrown earlier, by the command constructors in the inbound
adapter.

Implemented by `service/PasswordResetService`, which delegates the issuing step to `service/ResetCodeIssuer`
(research R6).

### `requestReset` (not transactional)

1. `now = clock.instant()`, `code = codeGenerator.newCode()`, `hash = codeHasher.hash(code)`. These run first, so
   every request takes the same path (research R11).
2. `userId = resetCodeIssuer.issue(contact, hash, now)`. This runs in its own transaction (below). If it throws
   `PasswordResetBusyException`: log the event and return.
3. If `userId` is empty: return.
4. `sender.send(contact, code)`. This happens after the commit. On `CodeDeliveryException`: log a warning with the
   user id and channel, never the code, and return normally (research R10).

### `ResetCodeIssuer.issue(Contact contact, String codeHash, Instant now)` (`@Transactional`, returns `OptionalLong`)

1. `user = userRepository.findForPasswordReset(contact)`. If empty: log at debug and return empty.
2. If `codes.countIssuedSince(userId, now - policy.resendInterval + 1 ms) > 0` or
   `codes.countIssuedSince(userId, now - 1h) >= policy.maxCodesPerHour`: log the limit and return empty. (The count
   includes its start time, so the resend window starts 1 ms later: a code issued exactly one interval ago no longer
   blocks.)
3. `codes.findOpen(userId).ifPresent(c -> { c.invalidate(now); codes.update(c); })`.
4. `codes.add(PasswordResetCode.issue(userId, codeHash, contact.channel(), now, policy.codeTtl))`, then return
   the account's id. Returning the id (instead of a boolean) lets the caller log it when delivery fails.

`ResetCodeIssuer` is a service-internal collaborator. It is not a port, it is used only by `PasswordResetService`,
and it lives in `service` so that transaction demarcation stays on application services (AR-06).

### `resetPassword` (`@Transactional(noRollbackFor = InvalidResetCodeException.class)`)

1. `now = clock.instant()`.
2. `user = userRepository.findForPasswordReset(contact)`. If empty, throw `InvalidResetCodeException`.
   `PasswordResetBusyException` propagates.
3. `code = codes.findOpen(userId)`. If empty or `!code.isUsable(now)`, throw `InvalidResetCodeException`.
4. If `!codeHasher.matches(command.code(), code.codeHash())`: `code.recordFailedAttempt(now, policy.maxAttempts)`,
   `codes.update(code)`, throw `InvalidResetCodeException`.
5. If `PasswordRules.usernameError(newPassword, user.username)` is not null, throw `InvalidPasswordResetException`.
6. If `newPassword` matches `user.password` or any of `history.findRecentHashes(userId, HISTORY_SIZE - 1)`
   (checked with `passwordHasher.compare`), throw `InvalidPasswordResetException`
   (`must not be one of your last 3 passwords`).
7. `history.add(PasswordHistoryEntry.of(userId, user.password, now))`, then
   `history.keepNewest(userId, HISTORY_SIZE - 1)`.
8. `user.changePassword(passwordHasher.hash(newPassword))`, `userRepository.update(user)`.
9. `code.markUsed(now)`, `codes.update(code)`.
10. `sessions.revokeAllForUser(userId, now)`.

## UserRepository (outbound, extended)

```java
/**
 * Finds the account whose email (lowercase) or phone equals the contact's address, and locks it against other
 * password-reset operations until the current transaction ends.
 *
 * @param contact a normalized contact.
 * @return the account, or empty if none has that address.
 * @throws PasswordResetBusyException if the lock is not acquired within the lock timeout.
 */
Optional<User> findForPasswordReset(Contact contact);

/**
 * Stores changes to an existing user.
 *
 * @param user a user loaded from this repository.
 * @return the stored user.
 */
User update(User user);
```

Implemented by `JpaUserRepository`. `SpringDataUserRepository` gains `findLockedByEmail` and `findLockedByPhone`.
Both are annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)` (rendered `FOR NO KEY UPDATE`), with no timeout
hint, because Hibernate's PostgreSQL dialect may ignore a positive lock timeout.
- **Setting the timeout**: before the locked query, `findForPasswordReset` runs
  `select set_config('lock_timeout', '<n>ms', true)` through the `EntityManager`, which is `SET LOCAL
  lock_timeout = '<n>ms'` with a bound parameter. `<n>` comes from `app.password-reset.lock-timeout`
  (default `15s`), bound by `adapter/persistence/PasswordResetLockProperties`.
- **Scope**: `SET LOCAL` lasts only until the transaction ends, so it never leaks to other transactions on the
  pooled connection. The method must be called inside a transaction, and both callers (`ResetCodeIssuer.issue`,
  `PasswordResetService.resetPassword`) are `@Transactional`.
- **Translation**: a timeout surfaces as Spring's `PessimisticLockingFailureException` (which covers
  `CannotAcquireLockException`). The method translates it into `PasswordResetBusyException` (research R6).

## SessionRepository (outbound, extended)

```java
/**
 * Ends every session of the user that has not ended yet.
 *
 * @param userId the account.
 * @param at     the revocation time to record.
 * @return the number of sessions ended.
 */
int revokeAllForUser(long userId, Instant at);
```

Implemented by `JpaSessionRepository` through a `@Modifying` JPQL bulk update in `SpringDataSessionRepository`.

## PasswordResetCodeRepository (outbound, new)

```java
public interface PasswordResetCodeRepository {
    /**
     * @return the stored code, with its id.
     * @throws PasswordResetBusyException if the account already has an open code (a concurrent issue that slipped
     *                                    past the account lock).
     */
    PasswordResetCode add(PasswordResetCode code);

    /**
     * Stores changes to a code loaded from this repository and flushes them, so closing an open code reaches the
     * database before a new open code is inserted.
     */
    PasswordResetCode update(PasswordResetCode code);

    /** @return the account's code that is neither used nor invalidated (it may be expired), if any. */
    Optional<PasswordResetCode> findOpen(long userId);

    /** @return how many codes were issued to the account at or after {@code since}. */
    long countIssuedSince(long userId, Instant since);
}
```

Implemented by `adapter/persistence/JpaPasswordResetCodeRepository` over `SpringDataPasswordResetCodeRepository`.
`add` uses `saveAndFlush`. A `DataIntegrityViolationException` naming `uq_password_reset_codes_open` becomes
`PasswordResetBusyException`; any other integrity violation becomes an `IllegalStateException` naming the
constraint. No Spring or driver exception crosses the port (constitution VII).

## PasswordHistoryRepository (outbound, new)

```java
public interface PasswordHistoryRepository {
    PasswordHistoryEntry add(PasswordHistoryEntry entry);

    /** @return up to {@code limit} password hashes, newest first. */
    List<String> findRecentHashes(long userId, int limit);

    /** Deletes all but the {@code keep} newest entries of the account. */
    void keepNewest(long userId, int keep);
}
```

Implemented by `adapter/persistence/JpaPasswordHistoryRepository`. `keepNewest` is a native `DELETE … WHERE id NOT
IN (SELECT id … ORDER BY replaced_at DESC, id DESC LIMIT :keep)`.

## ResetCodeHasher (outbound, new)

```java
public interface ResetCodeHasher {
    /**
     * @param code a 6-digit code.
     * @return a keyed one-way hash of the code, as 64 lowercase hex characters.
     */
    String hash(String code);

    /**
     * @return true if {@code code} hashes to {@code hash}; compares in constant time.
     */
    boolean matches(String code, String hash);
}
```

Implemented by `adapter/security/HmacResetCodeHasher`: HMAC-SHA256 keyed with `app.password-reset.code-secret`
(`APP_RESET_CODE_SECRET`, Base64, at least 32 bytes). The key comes from `ResetCodeHasherProperties` in
`adapter/security`, which is enabled from `config/SecurityConfig` (research R3). A missing or short key fails
startup with `IllegalStateException`, as the JWT secret does.

## ResetCodeSender (outbound, new)

```java
public interface ResetCodeSender {
    /**
     * Delivers a password-reset code to a contact.
     *
     * @throws CodeDeliveryException if the code could not be delivered.
     */
    void send(Contact contact, String code);
}
```

Implemented in `adapter/notification` by `LoggingResetCodeSender` (`app.password-reset.log-codes=true`) or
`UnavailableResetCodeSender` (otherwise), selected with `@ConditionalOnProperty` (research R9).

## ResetCodeGenerator (outbound, new)

```java
@FunctionalInterface
public interface ResetCodeGenerator {
    /** @return a new uniformly random code of exactly 6 digits, leading zeros kept. */
    String newCode();
}
```

Wired in `config/ApplicationConfig` over a single `SecureRandom` (research R12).

## Unchanged ports used

- `PasswordHasher.hash` / `compare`: BCrypt for the new password and for the comparison with the current and
  previous passwords. Codes use `ResetCodeHasher` instead (research R3).
- `Clock`: the injected bean.
