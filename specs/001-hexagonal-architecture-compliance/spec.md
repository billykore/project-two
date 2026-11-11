# Feature Specification: Hexagonal Architecture Compliance

**Feature Branch**: `001-hexagonal-architecture-compliance`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "Make sure the project is follows the best practice of hexagonal architecture"

## Overview

The project's constitution (v2.0.0, Principles IV, VI, VII and VIII) defines how the ride-hailing backend is meant to
be organized: a domain model and its ports at the center, adapters for the outside world around the edge, and every
dependency pointing inward. The current code only partly follows these rules. The constitution's Sync Impact Report
lists the known gaps as follow-up TODOs, and a survey of the code found a few more.

This feature brings the existing code in line with those principles and adds an automated guard so it stays that
way. It is a structural change: customers, drivers and other systems that call the backend must see no difference,
apart from one correction to when offer events are delivered (see FR-012).

The people this serves are the project's developers and reviewers: they need to change business rules, storage or
messaging without touching unrelated layers, test business rules without running infrastructure, and get told
automatically when a change breaks the dependency rule.

### Gaps this feature closes

| Ref | Gap found in the current code | Principle |
|-----|-------------------------------|-----------|
| G1  | No automated check of the dependency rule; violations are caught only in review (`TODO(ARCH_TEST)`). | IV |
| G2  | The input for creating a trip is defined in the application-service layer, yet the domain's inbound port and the HTTP adapter both depend on it (`TODO(CORE_IMPORTS_SERVICE)`). | IV, VI |
| G3  | The storage ports inherit from the data-access framework and one carries a framework query, so the domain's contracts are written in a framework's terms (`TODO(FRAMEWORK_PORTS)`). | VI |
| G4  | Ports contain logic: a hashing helper and a convenience method with behavior (`TODO(PORT_LOGIC)`). | VI |
| G5  | The trip service catches storage-driver exceptions to detect a duplicate active trip (`TODO(INFRA_EXCEPTIONS)`). | VII |
| G6  | Trip events go through the framework's in-process event mechanism, and the after-commit relay is a framework component living inside the domain package (`TODO(EVENT_PORT)`). | VII, VIII |
| G7  | Offer acceptance and rejection publish their events directly while the transaction is still open, so an event can go out for a change that is later rolled back. Trip creation and offer handling deliver events in two different ways. | VII, VIII |
| G8  | Services, the trip entity and the user entity read the system clock and generate identifiers directly (`TODO(CLOCK)`). | VIII |
| G9  | The fare calculator (a domain rule) sits in the shared-helpers package and depends on configuration; the HTTP error writer (transport-specific) sits there too and is used by an outbound adapter (`TODO(COMMON_PACKAGE)`). | IV |
| G10 | Trip-creation rules (known service type, pickup different from destination) are checked in the application service rather than by the trip itself. | VIII |
| G11 | The HTTP adapter makes decisions that are not transport concerns: it invents the trip's request identifier, and it substitutes customer `0` when the caller's identity is missing, or relies on a disabled-by-default assertion. | VII |
| G12 | "Service type" exists as two separate concepts in the domain (a stored record and an enumeration), with no single source of truth. | I, VIII |
| G13 | Outbound adapters are not grouped by the technology they wrap (storage, messaging, security), and the transaction boundary on the trip service uses a different mechanism from the other services. | IV, VIII |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Boundary violations are caught automatically (Priority: P1)

A developer changes code in a way that breaks the dependency rule, for example the domain starts depending on an
adapter, or an adapter reaches into an application service. The regular test run fails and names the offending
dependency, before the change can be merged.

**Why this priority**: every other story improves the structure once; this one keeps it improved. It also produces
the list of current violations that the other stories work down, so it is the right place to start.

**Independent Test**: add a deliberate forbidden dependency on a scratch branch and run the test suite; it fails and
names the dependency. Remove it and the suite passes.

**Acceptance Scenarios**:

1. **Given** the dependency rules from Principle IV, **When** the test suite runs, **Then** each rule (domain
   depends on nothing outward, inbound and outbound adapters do not depend on application services or on each
   other, shared helpers depend on nothing in the application) is checked automatically.
2. **Given** a change that adds a forbidden dependency, **When** the test suite runs, **Then** it fails and the
   report names the source and target of the forbidden dependency.
3. **Given** violations that already exist when the guard is introduced, **When** the guard is first added,
   **Then** those violations are listed explicitly as known exceptions, the suite passes, and any *new* violation
   still fails it.
4. **Given** a known exception that has been fixed, **When** the suite runs, **Then** the exception must be removed
   from the list (the list can only shrink), and at the end of this feature the list is empty.

---

### User Story 2 - Business rules can be tested without infrastructure (Priority: P2)

A developer wants to test trip booking and offer acceptance. Every port the application services use is a plain
contract in domain terms, so the developer can supply simple in-memory stand-ins for storage, events, clock and
identifiers, and test every rule without a database, broker, web server or framework context.

**Why this priority**: this is the main benefit hexagonal architecture is supposed to deliver, and it removes the
largest group of violations (G2, G3, G4, G8, G10).

**Independent Test**: write unit tests for the trip and offer application services using only in-memory stand-ins
and a fixed clock; they run and pass with no infrastructure available.

**Acceptance Scenarios**:

1. **Given** the storage contracts the application uses, **When** a developer inspects them, **Then** they declare
   only the operations the application actually calls, in domain types, with no framework inheritance,
   annotations, query text, static helpers or default methods with logic.
2. **Given** the input for creating a trip, **When** a developer looks for it, **Then** it is defined alongside the
   domain's ports, is immutable, and rejects invalid values when it is built.
3. **Given** a fixed clock and a predictable identifier source, **When** a trip is created or an offer accepted in
   a test, **Then** every timestamp and identifier in the result and in the raised events equals the supplied
   values.
4. **Given** a trip request with an unknown service type or identical pickup and destination, **When** it is
   processed, **Then** the trip itself rejects it, and the same rejection reaches the caller as before.

---

### User Story 3 - Infrastructure details stay in adapters (Priority: P3)

A developer replaces or changes the storage or messaging technology. Because every infrastructure concern (storage
errors, after-commit delivery, message format, error writing) lives in an adapter behind a port, the application
services and domain do not change.

**Why this priority**: closes the remaining boundary leaks (G5, G6, G7, G9, G11, G13) and fixes the one real defect
found, offer events that may describe a rolled-back change.

**Independent Test**: inspect the application services and domain for infrastructure exceptions, framework event
mechanisms and transport types (none remain), then run the scenarios below against the running system.

**Acceptance Scenarios**:

1. **Given** two concurrent requests that each try to create an active trip for the same customer, **When** both
   are processed, **Then** one succeeds and the other receives the same "active trip exists" conflict as today,
   with the storage error translated into the domain error inside the storage adapter.
2. **Given** an offer acceptance or rejection whose transaction rolls back, **When** processing ends, **Then** no
   event about it is delivered.
3. **Given** any trip or offer change that commits, **When** processing ends, **Then** its event is delivered once,
   after the commit, through the same outbound event port for every event type.
4. **Given** the event broker is unavailable after a change has committed, **When** delivery fails, **Then** the
   caller still receives a success response and the failure is logged, as today.
5. **Given** a request to an inbound endpoint without a usable caller identity, **When** it is processed, **Then**
   it is rejected as unauthorized rather than processed for a substitute customer.

---

### User Story 4 - The code layout tells the architecture (Priority: P4)

A new contributor opens the project and can tell from the package layout and the README where the domain, ports,
application services, inbound adapters, outbound adapters (grouped by technology) and wiring live, and finds each
domain concept defined once.

**Why this priority**: makes the result understandable and keeps documentation honest, but delivers no behavior on
its own.

**Independent Test**: compare the package layout and README Architecture section against the role table in
Principle IV; every class sits in the package its role requires, and each domain concept has one definition.

**Acceptance Scenarios**:

1. **Given** the outbound adapters, **When** a contributor browses them, **Then** they are grouped by the
   technology they wrap (storage, messaging, security).
2. **Given** the concept "service type", **When** a contributor searches the domain, **Then** it is defined once,
   and both the API and storage use that definition.
3. **Given** the shared-helpers package, **When** a contributor inspects it, **Then** it contains only
   technology-neutral helpers with no business rules (or no longer exists).
4. **Given** the README Architecture section and the CHANGELOG, **When** this feature is complete, **Then** they
   describe the new layout and the event-delivery correction.

---

### Edge Cases

- A storage error that is *not* the duplicate-active-trip case must still surface as a failure, not be swallowed or
  disguised as a conflict.
- An event raised in a transaction that rolls back must not be delivered (applies to trip and offer events alike).
- A change that commits but whose event fails to deliver must still succeed for the caller (existing behavior).
- Offer expiry is decided against the injected clock, so an offer exactly at its expiry instant behaves the same in
  tests and in production.
- The composition root (wiring and configuration) may depend on any package; the guard must not flag it.
- Persistence mapping annotations on domain entities are explicitly permitted by Principle VIII and must not be
  flagged; queries and constraint names must still live in adapters.
- A trip request repeated by the client must behave exactly as it does today; moving identifier generation out of
  the HTTP adapter must not introduce or remove deduplication.
- Existing stored data (service types, trips, offers) must keep working without data migration beyond what the
  service-type consolidation strictly requires.

## Requirements *(mandatory)*

### Functional Requirements

**Automated guard**

- **FR-001**: The test suite MUST automatically verify each dependency rule of Principle IV and fail, naming the
  offending dependency, when one is broken.
- **FR-002**: Violations present when the guard is introduced MUST be recorded as an explicit list of known
  exceptions that can only shrink; when this feature is complete the list MUST be empty.
- **FR-003**: The guard MUST also verify that ports contain no framework types, framework inheritance or
  annotations, and that the domain contains no framework component, transaction or event-listener annotations.

**Ports (Principle VI)**

- **FR-004**: Every outbound port MUST be a plain contract in domain and standard-library types, declaring only the
  operations the application calls, with no static helpers or default methods containing logic.
- **FR-005**: Storage behavior currently inherited from the data-access framework MUST be provided by storage
  adapters that implement the ports.
- **FR-006**: The input for creating a trip MUST be an immutable command defined with the domain's ports, and MUST
  validate its own invariants when built.
- **FR-007**: Each port MUST document the domain errors it can raise. Failures the application can act on (such as
  a concurrent request creating a second active trip) MUST cross the port only as domain exceptions. Unexpected
  infrastructure failures (lost connection, an integrity violation that is not a business rule, a conflict detected
  at commit) may propagate unchanged and end as a server error, as long as no application service catches them.
- **FR-008**: The SHA-256 hashing helper currently on a port MUST move behind a port or into a technology-neutral
  helper, so the port holds declarations only.

**Adapters (Principle VII)**

- **FR-009**: The storage adapter MUST translate a violation of the one-active-trip-per-customer rule into the
  domain's "active trip exists" error; application services MUST NOT catch storage, driver or framework
  exceptions.
- **FR-010**: Inbound HTTP adapters MUST only extract and validate input, map it to a command, call one use case,
  and map the result or domain error to the response. They MUST NOT generate business identifiers, substitute
  default identities, or rely on runtime assertions for input checks.
- **FR-011**: Outbound adapters MUST be grouped by the technology they wrap, and MUST NOT depend on application
  services or on one another. The HTTP error writer used by the authentication entry point MUST live with the HTTP
  edge, not in shared helpers.

**Events**

- **FR-012**: All domain events (trip requested, driver assigned, offer rejected, and any others) MUST be handed by
  the application to the outbound event port, and MUST be delivered only after the transaction that raised them
  commits. This changes current behavior for offer events, which today are sent before commit.
- **FR-013**: The after-commit delivery mechanism MUST live in an adapter, not in the domain, and a delivery failure
  after commit MUST be logged without failing the caller's request.

**Domain model (Principle VIII)**

- **FR-014**: Trip-creation rules (known service type, pickup and destination differ, initial status, fare) MUST be
  enforced by the trip and the fare domain service, not re-implemented in the application service.
- **FR-015**: Fare calculation MUST be a domain service built from plain values (rate, minimum fare), not dependent
  on configuration classes or framework beans.
- **FR-016**: Every timestamp and generated identifier that affects behavior or appears in an event MUST come from
  an injected clock or identifier source and be passed into domain methods; domain and application code MUST NOT
  read the system clock or generate random identifiers directly.
- **FR-017**: Transaction boundaries MUST be declared only on application-service implementations of inbound ports,
  all using the same mechanism.
- **FR-018**: "Service type" MUST have a single definition in the domain, used by the API and by storage.

**Behavior preservation and documentation**

- **FR-019**: Apart from FR-012 and the unauthorized-identity case in User Story 3, every HTTP endpoint MUST keep its
  paths, request fields, status codes, response bodies and Problem Details, and every event MUST keep its topic,
  key and payload.
- **FR-020**: Every existing test MUST keep passing without weakened assertions, and each moved or new rule MUST be
  covered by a test (Principle II).
- **FR-021**: The README Architecture section and the CHANGELOG (Unreleased) MUST be updated, and the constitution's
  Sync Impact Report TODOs closed by this feature MUST be marked resolved.
- **FR-022**: Outbound adapters created or changed by this feature (storage, event delivery) MUST be covered by
  tests against the real database and the real event broker, run as part of the regular test suite. These tests
  MUST cover at least: each storage operation the ports declare; the duplicate-active-trip translation under
  concurrent inserts; and, for events, the topic, key and payload of each event type, delivery only after commit,
  and no delivery after a rollback. Adapters this feature does not touch, and web-layer tests for every endpoint,
  stay out of scope.

### Key Entities

- **Dependency rule**: the allowed direction of dependencies between the six package roles of Principle IV
  (domain, application services, inbound adapters, outbound adapters, composition root, shared helpers).
- **Known-exception list**: the explicit, shrinking record of current rule violations the guard tolerates while
  the refactoring is in progress; empty at completion.
- **Inbound port (use case)**: a capability the outside world asks of the application (book a trip, accept or
  reject an offer, log in), taking a command and returning a domain result.
- **Outbound port**: something the application needs from the outside world (store trips, offers, drivers, users
  and sessions; publish events; issue tokens; hash secrets; tell the time; generate identifiers).
- **Create-trip command**: the immutable input for booking a trip: customer, request identifier, service type,
  pickup, destination, payment method.
- **Domain event**: a record of something that happened to a trip or offer, raised by the application and
  delivered by an adapter after commit.
- **Service type**: the kind of ride offered (MyRide, MyCar), with its vehicle and passenger limit; one definition.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The automated guard reports 0 dependency-rule violations, and its known-exception list is empty.
- **SC-002**: A deliberately introduced forbidden dependency fails the regular test run 100% of the time, and the
  failure message names both ends of the dependency.
- **SC-003**: All 13 gaps in the table above (G1–G13) are closed, and the 8 constitution TODOs they map to
  (`ARCH_TEST`, `CORE_IMPORTS_SERVICE`, `FRAMEWORK_PORTS`, `PORT_LOGIC`, `INFRA_EXCEPTIONS`, `EVENT_PORT`, `CLOCK`,
  `COMMON_PACKAGE`) are marked resolved.
- **SC-004**: 100% of application-service and domain unit tests run without a database, broker, web server or
  framework context, and none depends on wall-clock time.
- **SC-005**: 100% of tests that passed before the change still pass, and every endpoint returns the same status
  codes and body shapes for the same inputs (except the documented unauthorized-identity case).
- **SC-006**: 0 events are delivered for changes whose transaction rolled back, for every event type.
- **SC-007**: A contributor unfamiliar with the code can say which package a given class belongs in, using only the
  README and the package layout, for 10 out of 10 sampled classes.
- **SC-008**: Every storage and event-delivery adapter created or changed by this feature has passing tests against
  the real database and broker, including the concurrent duplicate-trip case and the rollback case.

## Assumptions

- The constitution v2.0.0 is the definition of "best practice" for this project; this feature does not amend it.
- Persistence mapping annotations stay on the domain entities, as Principle VIII permits; a separate persistence
  model is out of scope.
- The work is delivered incrementally, one user story (or smaller) per change, with the suite green after each
  step; the known-exception list from User Story 1 makes that possible.
- No new user-facing capability is added. Rejecting offers over HTTP, trip lifecycle endpoints after driver
  assignment, and registration remain out of scope.
- The trip request identifier keeps its current meaning; this feature only moves where it is generated.
- Event payloads keep their current shape, so consumers of the trip-events topic need no change.
- Service-type consolidation reuses the existing stored service types; any schema change goes in a new migration and
  no existing migration is edited (Principle V).
- New libraries are added only where needed for the guard and for the adapter integration tests of FR-022, and are
  justified in the plan.
- Running the full test suite requires a local container runtime, because the FR-022 tests start a real database
  and broker. This becomes a documented prerequisite for contributors.
- The constitution's `TODO(INTEGRATION_TEST_INFRA)` is partly addressed: the test infrastructure exists and covers
  the adapters this feature touches. Coverage of the remaining adapters and endpoint web-layer tests is follow-up
  work.

## Clarifications

### Session 2026-11-11

- Q: Should this feature also set up the real-database and real-broker test infrastructure that Principle III
  requires? → A: Yes, but only for the outbound adapters this feature creates or changes (option A).
- Q: Must every infrastructure exception be translated before it crosses a port (FR-007)? → A: No. Only failures
  the application can act on are translated. Unexpected ones propagate unchanged, because conflicts detected at
  commit can't be translated by any adapter (plan research R3; raised by `/speckit-analyze` H1 and converge T084).
