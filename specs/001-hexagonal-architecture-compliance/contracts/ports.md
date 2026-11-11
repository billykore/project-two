# Contract: Ports (`org.kore.myapp.core.port`)

These are the hexagon's internal contracts after the refactor. Every port follows the same rules:
- it is a plain Java interface;
- it uses only domain types (`core.*`) and JDK types;
- it has no static or default methods and no framework supertypes or annotations;
- it documents each domain exception it raises with `@throws`.

These rules are enforced by `ArchitectureTest` ([architecture-rules.md](./architecture-rules.md)).

Signatures are shown in Java for precision. Javadoc in the source carries the full `@throws` text.

## Inbound ports (implemented in `service`)

```java
public interface TripUseCase {
    /** @throws InvalidTripException       unknown service type, or pickup equals destination
     *  @throws ActiveTripExistsException  the customer already has an unfinished trip */
    Trip create(CreateTripCommand command);
}

public interface TripOfferUseCase {
    /** @throws DriverNotFoundException, OfferNotAvailableException, TripNotFoundException */
    Trip accept(UUID tripId, long driverUserId);
    /** @throws DriverNotFoundException */
    void reject(UUID tripId, long driverUserId);
}

public interface AuthUseCase {
    /** @throws InvalidCredentialsException, TokenIssuanceException */
    Token login(String username, String password);
}

public interface HelloUseCase {
    /** @throws UserNotFoundException */
    String sayHello(String username);
}
```

The only signature change is that `TripUseCase.create` now takes `core.valueobject.CreateTripCommand` (moved from
`service.command`).

## Outbound ports (implemented in `adapter/*` or `config`)

```java
public interface TripRepository {                                  // adapter.persistence.JpaTripRepository
    Optional<Trip> findById(UUID id);
    /** A trip of the customer in one of {@link TripStatus#activeStatuses()}, if any. */
    Optional<Trip> findActiveByCustomerId(long customerId);
    /** Inserts a new trip and makes the insert visible to constraint checks immediately.
     *  @throws ActiveTripExistsException  a concurrent request created an unfinished trip for the same customer */
    Trip add(Trip trip);
    Trip update(Trip trip);
}

public interface TripOfferRepository {                             // adapter.persistence.JpaTripOfferRepository
    /** The PENDING offer of the trip for this driver, if any. */
    Optional<TripOffer> findPending(UUID tripId, long driverId);
    TripOffer update(TripOffer offer);
}

public interface DriverProfileRepository {                         // adapter.persistence.JpaDriverProfileRepository
    Optional<DriverProfile> findByUserId(long userId);
    DriverProfile update(DriverProfile driver);
}

public interface UserRepository {                                  // adapter.persistence.JpaUserRepository
    Optional<User> findByUsername(String username);
}

public interface SessionRepository {                               // adapter.persistence.JpaSessionRepository
    Session add(Session session);
}

public interface ServiceTypeRepository {                           // adapter.persistence.JpaServiceTypeRepository
    /** The active service type with exactly this name (case-sensitive), if any. */
    Optional<ServiceType> findActiveByName(String name);
}

public interface EventPublisher {                                  // adapter.kafka.AfterCommitEventPublisher
    /** Hands an event over for delivery. If called inside a transaction, the event is delivered only after
     *  that transaction commits and is discarded if it rolls back. Delivery failures after commit are
     *  logged and never thrown to the caller.
     *  @throws EventPublishException  only when called outside a transaction and delivery fails */
    void publish(Event event);
}

public interface TokenIssuer {                                     // adapter.security.JwtTokenIssuer
    /** @throws TokenIssuanceException */
    Token issue(User user);
}

public interface PasswordHasher {                                  // adapter.security.BcryptPasswordHasher
    String hash(String rawPassword);
    boolean compare(String rawPassword, String hashedPassword);
}

/** NEW: replaces the static GeneralHasher.sha256Hex helper. */
public interface TokenHasher {                                     // adapter.security.Sha256TokenHasher
    /** One-way hash of an access token for storage; lowercase hex. */
    String hash(String token);
}

/** NEW: source of identifiers that appear in domain objects and events. */
public interface IdGenerator {                                     // config.ApplicationConfig: UUID::randomUUID
    UUID newId();
}
```

`java.time.Clock` is injected directly. It is a JDK type, so no port wraps it, and `config.ApplicationConfig`
provides `Clock.systemUTC()`.

## Removed from ports

| Removed | Reason |
|---------|--------|
| `extends JpaRepository<…>` on all six repositories | Principle VI. CRUD is now the adapter's business. |
| `TripRepository.findFirstByCustomerIdAndStatusIn` (public derived query) | Implementation detail; moved to `SpringDataTripRepository`. |
| `default findActiveByCustomerId` | Ports have no default methods; it is now a plain declaration. |
| `@Query` on `TripOfferRepository.findPending` | Moved to `SpringDataTripOfferRepository`. |
| `GeneralHasher` (static helper) | Replaced by the `TokenHasher` port. |

## Test doubles

Every outbound port can be replaced in `service` unit tests by a Mockito mock, a lambda (`IdGenerator`), a
`Clock.fixed(...)`, or the in-memory `RecordingEventPublisher` in `src/test/.../service/support`. None of these
requires a Spring context.
