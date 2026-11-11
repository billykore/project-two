# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gates from `.specify/memory/constitution.md` (v2.0.0). Mark each ✅ pass, or ❌ with a row in Complexity Tracking.

- [ ] **I. Clean, Readable Code** — domain names, constructor injection, immutable DTOs/value objects, specific
  domain exceptions mapped to Problem Details, no secrets in source.
- [ ] **II. Test Discipline** — every behavior change has unit tests (no Spring context needed for domain logic);
  bug fixes start with a failing regression test; error and invariant paths covered.
- [ ] **III. Integration & Contract Verification** — migrations/queries tested against PostgreSQL, endpoints have
  web-layer tests, events have payload/after-commit tests, DB-backed concurrency rules have a conflict test.
- [ ] **IV. Hexagonal Boundaries (Dependency Rule)** — every new class is placed in its package role (`core`,
  `service`, `api`, `adapter/<tech>`, `config`, `common`); `core` imports no other application package; `api` and
  `adapter` reach the application only through `core/port`; the ArchUnit test stays green.
- [ ] **V. Maintainability & Simplicity** — no speculative abstractions or unjustified dependencies; new migration
  instead of editing a merged one; tunables in `@ConfigurationProperties`; CHANGELOG and README updated.
- [ ] **VI. Ports Are Owned by the Application** — each new inbound port is a `*UseCase`; each outbound port is
  named for the capability; signatures use only domain/JDK types; ports don't extend `JpaRepository` or carry
  framework annotations; use-case input is a command record in `core`.
- [ ] **VII. Adapters Translate, Never Decide** — controllers only validate, map, call one use case, and map the
  result; outbound adapters in `adapter/<tech>` translate infrastructure exceptions into domain exceptions; no
  adapter calls another adapter.
- [ ] **VIII. Domain Model at the Center** — invariants and state transitions live in entities/value objects;
  domain code has no Spring beans or annotations (JPA mapping excepted); time and ids are injected; transactions
  are on application services; events are handed to an outbound port.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: This project (DEFAULT) — hexagonal layout, see constitution Principle IV
src/main/java/org/kore/myapp/
├── core/
│   ├── entity/          # Domain entities (JPA mapping annotations allowed)
│   ├── valueobject/     # Immutable value objects, enums, commands, domain events
│   ├── exception/       # Domain exceptions
│   └── port/            # Inbound *UseCase ports and outbound ports (plain interfaces)
├── service/             # Application services implementing *UseCase ports (@Transactional here)
├── api/                 # Inbound HTTP adapter: controllers, request/response DTOs, exception handlers
├── adapter/
│   ├── persistence/     # Spring Data repositories + port implementations
│   ├── kafka/           # Event publishing
│   └── security/        # JWT, hashing
├── config/              # Composition root: wiring, security config, @ConfigurationProperties
└── common/              # Technology-neutral helpers only
src/main/resources/db/migration/   # Flyway migrations (never edit a merged one)

src/test/java/org/kore/myapp/
├── core/                # Plain unit tests of domain logic
├── service/             # Unit tests with test doubles for outbound ports
├── api/                 # @WebMvcTest web-layer tests
├── adapter/             # Integration tests against PostgreSQL/Kafka (Testcontainers)
└── ArchitectureTest.java  # ArchUnit rules for the dependency rule

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
