<!--
Sync Impact Report
==================
Version change: 1.0.0 → 2.0.0
Bump rationale: MAJOR. Principle IV was redefined in a backward-incompatible way: it previously allowed
Spring Data repository interfaces in `core`, and now forbids framework types in ports. Three hexagonal
principles (VI–VIII) were also added.

Modified principles:
- IV. Hexagonal Boundaries → IV. Hexagonal Boundaries (Dependency Rule): package roles defined, allowed
  dependency directions listed, `common` restricted, enforcement by an architecture test required

Added principles:
- VI. Ports Are Owned by the Application
- VII. Adapters Translate, Never Decide
- VIII. Domain Model at the Center

Added sections: none (Technology & Quality Standards and Development Workflow & Quality Gates expanded)

Removed sections: none

Templates requiring updates:
- ✅ .specify/templates/plan-template.md: Constitution Check covers IV, VI, VII, VIII; source tree shows this
  project's hexagonal layout
- ✅ .specify/templates/tasks-template.md: implementation order follows domain → ports → service → adapters
- ✅ .claude/skills/speckit-implement/SKILL.md: core-development step follows the hexagonal order
- ✅ .specify/templates/spec-template.md: reviewed; no change (specs stay technology-agnostic)
- ✅ .specify/templates/checklist-template.md: reviewed; no change
- ✅ README.md: reviewed; its Architecture section describes the current layout and stays accurate

Follow-up TODOs (existing code that does not yet meet the principles; fix incrementally):
- TODO(INTEGRATION_TEST_INFRA): Testcontainers is not set up yet (Principle III).
- TODO(ARCH_TEST): add ArchUnit and `src/test/java/org/kore/myapp/ArchitectureTest.java` (Principle IV).
- TODO(CORE_IMPORTS_SERVICE): `CreateTripCommand` lives in `service.command`, but `core/port/TripUseCase` and
  `api/TripController` depend on it. Move it into `core` (Principles IV, VI).
- TODO(FRAMEWORK_PORTS): the repository ports in `core/port` extend `JpaRepository`, and `TripOfferRepository`
  carries a JPQL `@Query`. Make them plain interfaces implemented by Spring Data adapters under
  `adapter/persistence` (Principle VI).
- TODO(PORT_LOGIC): `GeneralHasher` holds a static helper and `TripRepository` a default method. Ports contain
  declarations only (Principle VI).
- TODO(INFRA_EXCEPTIONS): `TripService` catches `DataIntegrityViolationException` and Hibernate's
  `ConstraintViolationException`. Translate them in the persistence adapter (Principle VII).
- TODO(EVENT_PORT): `TripService` uses Spring's `ApplicationEventPublisher`, and `core/event/TripEventRelay` is a
  Spring `@Component` with `@TransactionalEventListener`. Publish through a port and move the relay to `adapter`
  (Principles VII, VIII).
- TODO(CLOCK): `TripService`, `OfferService` and `Trip.requestedAt` call `Instant.now()`. Inject a `Clock`
  (Principle VIII).
- TODO(COMMON_PACKAGE): `common/FareCalculator` is a domain service that depends on `config`, and
  `common/ProblemDetailWriter` is HTTP-specific. Move them to `core` and `api` respectively (Principle IV).
-->

# myapp Constitution

## Core Principles

### I. Clean, Readable Code

Code is written for the next reader first and the compiler second.

- Names MUST state intent in the language of the domain (`Trip`, `TripOffer`, `FareCalculator`), not of the
  mechanism (`Manager`, `Helper`, `Util`, `Data`).
- Each class MUST have one reason to change; a method that needs a comment to explain *what* it does MUST be split
  or renamed. Comments explain *why*.
- Dependencies MUST be injected through constructors; field injection and service-locator lookups are forbidden.
- Value objects and DTOs MUST be immutable (Java `record` or final fields). Mutable state is confined to entities and
  changed only through methods that enforce their invariants.
- Failures MUST be expressed as specific domain exceptions in `core/exception` and mapped to RFC 9457 Problem Details
  at the HTTP edge. Catching and swallowing an exception, or returning `null` to signal failure, is forbidden.
- Dead code, commented-out code, unused imports, and compiler warnings MUST NOT be merged.
- Secrets and environment-specific values MUST NOT appear in source or committed config; they come from environment
  variables or the git-ignored `.env`.

**Rationale**: the project exists partly to learn Spring Boot well. Code that reads clearly teaches; code that
hides intent behind generic names and silent failures has to be relearned every time it is touched.

### II. Test Discipline (NON-NEGOTIABLE)

No behavior changes without a test that proves it.

- Every change to behavior in `core`, `service`, `common`, or `api` MUST ship with automated tests in the same
  change. Refactors with no behavior change MUST keep the existing suite green without weakening assertions.
- Every bug fix MUST begin with a regression test that fails before the fix and passes after it.
- Domain logic (entities, value objects, services, calculators) MUST be covered by plain unit tests that run without
  a Spring context, a database, or a network — as `TripTest`, `CoordinateTest`, and `TripServiceTest` do today.
- Tests MUST be deterministic: no reliance on wall-clock time, random values, test ordering, or shared mutable
  state. Inject a `Clock` or fixed values instead.
- Each test MUST verify one behavior and be named for it (`createRejectsSamePickupAndDestination`), using the
  Given/When/Then shape of the spec's acceptance scenarios.
- Invariant and error paths (invalid input, conflicts, state-machine violations) MUST be tested, not only the
  happy path.
- `./mvnw test` MUST pass before any merge to `main`.

**Rationale**: trip state, fares, and driver assignment are rules where a silent regression costs real money and
trust. Fast unit tests make those rules safe to change.

### III. Integration & Contract Verification

What unit tests cannot see MUST be verified against the real thing.

- New or changed Flyway migrations, repository queries, and database constraints MUST be covered by a test that
  runs against PostgreSQL (Testcontainers), not an in-memory substitute.
- Every HTTP endpoint MUST have a web-layer test (`@WebMvcTest` or equivalent) asserting its status codes,
  response body shape, and Problem Details for each documented error.
- Every Kafka event type MUST have a test asserting its topic, key (`tripId`), and JSON payload, and that it is
  published only after the transaction commits.
- Concurrency rules that rely on the database (unique indexes, optimistic locking via `version`) MUST have a test
  that exercises the conflicting case.
- Changing a public contract (endpoint, request/response field, event payload) MUST update its tests and the README
  API section in the same change.

**Rationale**: the most serious bugs so far (concurrent trip creation, driver assignment) lived at the boundary
between code and database. Those boundaries need tests that include the boundary.

### IV. Hexagonal Boundaries (Dependency Rule)

The application is a hexagon: the domain and its ports in the middle, adapters around the edge, and every
source-code dependency pointing inward.

Package roles under `org.kore.myapp`:

| Package   | Role                                                                                    |
|-----------|-----------------------------------------------------------------------------------------|
| `core`    | Domain model (`entity`, `valueobject`, `exception`, domain services) and all ports      |
| `service` | Application services: implement inbound ports, orchestrate domain objects and out-ports |
| `api`     | Inbound (driving) adapter for HTTP: controllers, request/response DTOs, error handlers  |
| `adapter` | Outbound (driven) adapters, grouped by technology: `persistence`, `kafka`, `security`   |
| `config`  | Composition root: Spring wiring, security setup, `@ConfigurationProperties`             |
| `common`  | Technology-neutral helpers with no business rules and no framework dependencies         |

- Allowed dependencies: `api` → `core`; `service` → `core`; `adapter` → `core`; `config` → any package.
  `common` may be used by any package and depends on nothing in the application.
- `core` MUST NOT import from `api`, `service`, `adapter`, `config`, or `common`.
- `api` and `adapter` MUST NOT import from `service` or from each other; they reach the application only through
  ports in `core/port`.
- A new top-level package, or a dependency outside these rules, MUST be justified in the plan's Complexity
  Tracking table.
- These rules MUST be enforced by an automated architecture test (ArchUnit) in `src/test`, so a violation fails
  `./mvnw test` instead of relying on review.

**Rationale**: the dependency rule keeps the domain testable in isolation (Principle II) and lets HTTP, the
database, or Kafka change without rewriting business rules. An automated check makes the rule cheap to keep.

### V. Maintainability & Simplicity

Build what the current spec needs, in a way the next change can extend.

- Implement the simplest design that satisfies the spec (YAGNI). Abstractions, configuration switches, and new
  dependencies MUST be justified by a present requirement, not an anticipated one.
- A merged Flyway migration MUST NOT be edited; schema changes go in a new `V<n>__<description>.sql`.
- Business rules MUST be enforced in the domain, and data-integrity rules SHOULD also be enforced by database
  constraints, as the existing `trips` and `driver_profiles` tables do.
- Tunable values (rates, timeouts, TTLs, topic names) MUST be externalized as typed `@ConfigurationProperties`,
  not hard-coded.
- Every user-visible change MUST update `CHANGELOG.md` under **Unreleased**, and README sections it affects
  (API, configuration, feature status) in the same change.
- Duplicated logic MUST be consolidated once it appears a third time.

**Rationale**: the codebase is meant to grow feature by feature; small, documented, reversible steps keep it
understandable as it does.

### VI. Ports Are Owned by the Application

Ports are the contract of the hexagon, written in the application's terms, not a framework's.

- Every interaction between the application and the outside world MUST go through an interface in `core/port`.
  - **Inbound ports** are named `<Capability>UseCase` (`TripUseCase`, `TripOfferUseCase`) and are implemented
    by `service`.
  - **Outbound ports** are named for what the application needs, not for the technology that provides it
    (`TripRepository`, `EventPublisher`, `TokenIssuer`, not `JpaTripDao` or `KafkaSender`).
- Port signatures MUST use only domain types (entities, value objects, commands, domain exceptions) and JDK
  types. Spring, JPA/Hibernate, servlet, Kafka, JWT, and `api` DTO types MUST NOT appear in a port.
- Ports MUST NOT extend framework interfaces (such as `JpaRepository`) or carry framework annotations (such as
  `@Query`). An outbound port declares only the operations the application actually calls.
- Ports contain declarations only: no static helpers and no `default` methods with logic.
- Use-case input MUST be an immutable command or query record defined in `core`, which validates its own
  invariants in its constructor. Use-case output is a domain object or a result record from `core`.
- A port MUST document, with `@throws`, each domain exception it can raise; it MUST NOT leak infrastructure
  exceptions.

**Rationale**: a port written in domain terms can be implemented by a database today and an in-memory fake in a
test, and the application never notices the difference.

### VII. Adapters Translate, Never Decide

Adapters convert between the outside world and the application's ports; business decisions live inside.

- **Inbound adapters** (controllers in `api`, and future Kafka consumers or schedulers) MUST only: authenticate
  and extract input, validate its syntax (Bean Validation), map it to a command, call one use case, and map the
  result or domain exception to the transport (DTO, status code, Problem Details). They MUST NOT contain business
  rules or call repositories.
- **Outbound adapters** live in `adapter/<technology>` and implement an outbound port. Spring Data interfaces,
  JPQL and SQL, Kafka templates, and JWT libraries belong here and nowhere inward.
- Outbound adapters MUST translate infrastructure exceptions (for example `DataIntegrityViolationException`,
  Kafka send failures, JWT encoding errors) into domain exceptions before they cross the port. Application
  services MUST NOT catch framework or driver exceptions.
- Adapters MUST NOT call other adapters directly. Every flow goes adapter → inbound port → application →
  outbound port → adapter.
- Transport details (JSON shape, Kafka topic and key, HTTP headers, token claims) are decided in the adapter; the
  domain hands over domain objects only.
- Every outbound adapter MUST have an integration test against the real technology (Principle III), and every
  outbound port MUST be replaceable by a test double in unit tests of `service`.

**Rationale**: thin adapters are cheap to replace and to test; logic that leaks into them ends up untested and
duplicated across transports.

### VIII. Domain Model at the Center

Business rules live in the domain, and the domain stays free of infrastructure.

- Invariants and state transitions MUST be enforced by the entities and value objects that own them (for example,
  `Trip` decides which status changes are legal and when a driver is required). Application services orchestrate:
  they load, call domain methods, save, and publish. They MUST NOT re-implement rules that belong to one aggregate.
- Rules that span aggregates or need configuration (such as fare pricing) are domain services in `core`, built
  from plain values rather than Spring beans.
- Domain code (`core/entity`, `core/valueobject`, domain services) MUST NOT depend on Spring beans or annotations
  (`@Component`, `@Transactional`, `@EventListener`), `ApplicationEventPublisher`, servlet, HTTP, or Kafka types.
  JPA mapping annotations on entities are the one permitted exception, so the project does not need a duplicate
  persistence model. Queries and constraint names stay in adapters.
- Time and generated identifiers that affect behavior MUST come from an injected `Clock` or a port, and be passed
  into domain methods as arguments (as `Trip.assignDriver(driver, now)` already does). `Instant.now()` and
  `UUID.randomUUID()` MUST NOT be called inside domain logic.
- Transactions MUST be demarcated in application services (`@Transactional` on inbound port implementations),
  never in controllers, adapters, or domain objects.
- Domain events (such as `TripEvent`) are domain types raised by the application and handed to an outbound port;
  when and how they are delivered (after commit, to Kafka) is decided by adapters.

**Rationale**: a domain model without infrastructure dependencies can be understood, tested, and changed on its
own, and it is the part of the system that carries the business value.

## Technology & Quality Standards

- **Runtime**: Java 17, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server).
- **Architecture**: hexagonal (ports & adapters), per Principles IV, VI, VII, and VIII.
- **Persistence**: PostgreSQL with Flyway migrations; optimistic locking via `version` columns where concurrent
  updates are possible; Spring Data repositories are implementation details of `adapter/persistence`.
- **Messaging**: Apache Kafka; domain events published after commit, keyed by aggregate id to preserve order.
- **Build**: Maven through the committed wrapper (`./mvnw`); builds MUST NOT depend on a locally installed Maven.
- **Testing**: JUnit 5, AssertJ, Mockito; Testcontainers for PostgreSQL and Kafka integration tests; ArchUnit
  for architecture rules.
- **Errors**: all HTTP errors are RFC 9457 Problem Details (`application/problem+json`).
- **Security**: every endpoint except `/auth/**` requires a valid JWT; passwords use BCrypt; tokens are stored only
  as hashes.
- Adding a runtime dependency MUST be justified in the plan; prefer what Spring Boot already provides.

## Development Workflow & Quality Gates

1. **Spec → Plan → Tasks → Implement**: non-trivial features go through the spec-kit flow; each plan MUST pass the
   Constitution Check before research and again after design.
2. **Inside-out implementation**: within a user story, work proceeds from the domain outward: domain model and
   its tests → ports → application service and its tests → outbound adapters and their integration tests →
   inbound adapter and its web-layer tests.
3. **Tests with the change**: test tasks are part of every user story in `tasks.md`; for bug fixes, the failing test
   comes first.
4. **Before merge**, a change MUST:
   - pass `./mvnw test`, including the architecture test, with no new compiler warnings;
   - include unit tests (Principle II) and, where it touches persistence, HTTP, or events, integration tests
     (Principle III);
   - respect the hexagonal rules (Principles IV, VI, VII, VIII);
   - update `CHANGELOG.md` and affected README sections (Principle V).
5. **Review**: every pull request to `main` is reviewed against this constitution. A reviewer MUST block a change
   that violates a MUST rule unless the violation is recorded and justified in the plan's Complexity Tracking.
   Code that already violates a rule (see the Sync Impact Report TODOs) MUST NOT be made worse by a change, and
   a change touching it SHOULD move it toward compliance.
6. **Small commits**: one logical change per commit, with a message that says why.

## Governance

- This constitution supersedes conflicting practices, templates, and habits. When a template or command disagrees
  with it, the constitution wins and the template is fixed.
- **Amendments** are made by pull request that edits this file, updates the Sync Impact Report, and propagates the
  change to `.specify/templates/` and any affected command or guidance files in the same pull request.
- **Versioning** follows semantic versioning:
  - MAJOR: a principle is removed or redefined in a backward-incompatible way;
  - MINOR: a principle or section is added or materially expanded;
  - PATCH: clarifications and wording fixes with no change in meaning.
- **Compliance**: every plan runs the Constitution Check; every review verifies the Quality Gates. Justified
  exceptions live in the plan's Complexity Tracking table and SHOULD be paid down with a follow-up task.
- Runtime development guidance (how to run, configure, and call the app) lives in `README.md`.

**Version**: 2.0.0 | **Ratified**: 2026-11-11 | **Last Amended**: 2026-11-11
