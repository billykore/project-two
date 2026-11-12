# Port Contracts: User Registration

All ports are in `org.kore.myapp.core.port`. They are plain interfaces with no framework types, annotations, or
default methods (constitution VI).

## RegistrationUseCase (inbound, new)

```java
public interface RegistrationUseCase {
    /**
     * Creates a customer account.
     *
     * @param command validated, normalized registration input.
     * @return the saved user, with its id.
     * @throws RegistrationConflictException if the username, email, or phone already belongs to an account;
     *         names every such field, or (in a concurrent race) at least one of them.
     */
    User register(RegisterUserCommand command);
}
```

`InvalidRegistrationException` is thrown earlier, by the `RegisterUserCommand` constructor in the inbound adapter,
so it is not part of this port.

Implemented by `service/RegistrationService` (`@Transactional`):
1. Collect taken fields with `existsByUsername`, `existsByEmail`, `existsByPhone`; if any, throw
   `RegistrationConflictException(fields)`.
2. `hash = passwordHasher.hash(command.password())`.
3. `return userRepository.add(User.register(command, hash))`.

## UserRepository (outbound, extended)

```java
public interface UserRepository {
    Optional<User> findByUsername(String username);   // existing, unchanged

    /** @return true if an account has exactly this username. */
    boolean existsByUsername(String username);

    /** @param email lowercase email. @return true if an account has this email. */
    boolean existsByEmail(String email);

    /** @param phone international-format phone. @return true if an account has this phone. */
    boolean existsByPhone(String phone);

    /**
     * Inserts a new user and checks database constraints immediately.
     *
     * @return the saved user, with its id.
     * @throws RegistrationConflictException if a unique constraint on username, email, or phone rejects the
     *         insert (for example a concurrent registration); names the field concerned.
     */
    User add(User user);
}
```

Implemented by `adapter/persistence/JpaUserRepository` over `SpringDataUserRepository`, which gains the derived
queries `existsByUsername`, `existsByEmail`, and `existsByPhone`. `add` uses `saveAndFlush` and translates
`DataIntegrityViolationException` using the constraint names in research R4; other violations propagate.

## Unchanged ports used

- `PasswordHasher.hash(String)`: BCrypt via the existing `BcryptPasswordHasher`. The command guarantees ≤ 72
  bytes before it is called.
