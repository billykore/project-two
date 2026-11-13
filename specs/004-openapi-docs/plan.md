# Implementation Plan: OpenAPI Documentation from Code

**Branch**: `004-openapi-docs` | **Date**: 2026-11-13

## Summary

Generate the OpenAPI 3.1 document from the HTTP adapter instead of maintaining it by hand.
- springdoc reads the controllers and request/response records.
- Annotations on the controllers add what springdoc cannot infer:
  - error responses with Problem Details schemas and examples;
  - operation ids, summaries, and tags;
  - public versus Bearer-protected operations.
- `@Schema` on the records documents field rules and examples.
- The document and Swagger UI are served only when `APP_API_DOCS_ENABLED=true`.
- A copy is checked in as `docs/openapi.yaml` and kept in sync by a test.

## New runtime dependency (constitution: Technology & Quality Standards)

`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0`.

- **Why**: Spring Boot does not generate OpenAPI documents itself. Writing the document by hand drifts from the code; the first hand-written version already had to describe unmapped errors by reading the code.
- **Version**: 3.2.0 is the current springdoc release line for Spring Boot 4.x. It is built against Spring Boot 4.1.2, and this project uses 4.1.1.
- **Alternatives**:
  - A hand-written `docs/openapi.yaml`: no dependency, but nothing keeps it true.
  - `springdoc-openapi-maven-plugin`: generates at build time, but needs the full application running, with PostgreSQL and Kafka.

## Constitution Check

- **I.** Documentation-only types are records in `api/doc`. Example bodies are constants in `ApiDocs`, because annotation values must be compile-time constants.
- **II/III.** `OpenApiDocumentTest` (`@WebMvcTest`, no containers) fails when `docs/openapi.yaml` differs from the generated document, so a contract change is visible in review. `ApiDocsDisabledByDefaultTest` proves the docs are off by default.
- **IV/VII.** Annotations live only in `api` (the HTTP adapter) and `config`. `core` has no new dependency, and ArchUnit AR-01..AR-09 stay green.
- **V.** The new tunable is externalized: `APP_API_DOCS_ENABLED` defaults to `false`. README, CHANGELOG, and `.env.example` are updated.
- **Security.** The docs paths are public, and a Bearer token sent to them is ignored, the same as for `/auth/**`. When the docs are disabled, those paths answer `404`.

## Known limits

- The registration and password-reset field rules are enforced in `core` commands. The `@Schema` attributes on the request records repeat them for readers only. The sync test catches contract drift between the controllers and the document, not drift between those rules and their description.
- `POST /driver/offers/{tripId}/accept` has errors that no handler maps: no driver profile, no pending offer, unknown trip. The document says so rather than inventing a status. Fixing it is a separate change.
