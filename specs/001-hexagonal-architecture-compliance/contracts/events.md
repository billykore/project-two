# Contract: Trip events (Kafka topic `trip-events`)

External consumers depend on this contract. The **payloads don't change** with this feature (FR-019). What changes is
the **delivery guarantee for offer events** (FR-012).

## Transport

| Property | Value | Change |
|----------|-------|--------|
| Topic | `app.kafka.trip-events-topic` (default `trip-events`) | Read through `TripEventsProperties` instead of `@Value`; same property name |
| Key | `aggregateId` = `tripId` as a string | unchanged |
| Value | JSON (UTF-8 string) | unchanged |
| Producer acks | `all` | unchanged |
| Send timeout | `app.kafka.publish-timeout` (default `5s`) | unchanged |
| Ordering | All events of one trip share a key, so they share a partition and keep their order. | unchanged |

## Delivery guarantee

| Event | Before | After |
|-------|--------|-------|
| `Requested` | after commit | after commit (unchanged) |
| `DriverAssigned` | **inside the transaction**, so it could describe a change that then rolled back | **after commit**; never delivered after a rollback |
| `Rejected` | **inside the transaction** | **after commit** |
| `Completed`, `Cancelled` | not raised yet | after commit, when raised |

At-most-once: if the broker can't be reached after the commit, the failure is logged and the event is lost. The
caller's request still succeeds (unchanged behavior; an outbox is out of scope, see research R4).

## Payloads (unchanged)

Every message has `eventId` (UUID), `occurredAt` (ISO-8601 instant), `tripId` (UUID) and `aggregateId` (= `tripId`).
The field order below is how today's serializer writes the record components, followed by `aggregateId`.

```json
// Requested
{"eventId":"…","occurredAt":"2026-11-11T08:00:00Z","tripId":"…","customerId":42,"serviceTypeId":1,
 "pickup":{"latitude":-6.1754,"longitude":106.8272},"aggregateId":"…"}

// DriverAssigned
{"eventId":"…","occurredAt":"…","tripId":"…","driverId":7,"aggregateId":"…"}

// Rejected
{"eventId":"…","occurredAt":"…","tripId":"…","aggregateId":"…"}

// Completed (defined, not raised yet)
{"eventId":"…","occurredAt":"…","tripId":"…","fare":{…},"aggregateId":"…"}

// Cancelled (defined, not raised yet)
{"eventId":"…","occurredAt":"…","tripId":"…","cancelledBy":"CUSTOMER","aggregateId":"…"}
```

The JSON keys must be identical before and after the change. Before the refactor, capture the exact current
serialization of each event type as a golden JSON fixture under `src/test/resources/events/`.
`AfterCommitEventPublisherIT` then asserts that each message received from Kafka equals its fixture (ignoring the
values of `eventId` and `occurredAt`, which the test supplies through a fixed `IdGenerator` and `Clock`). Because the
fixtures are captured first, a mix-in that fails to reproduce `aggregateId` is caught (research R4).

`serviceTypeId` keeps the same numbers (`1` for MyRide, `2` for MyCar). It is now `ServiceType.getId()` instead of the
enum ordinal, and the two happen to match for the seed data (research R9).

## Verified by

`adapter/kafka/AfterCommitEventPublisherIT`, run against a real Kafka container:
1. Topic, key and payload of each event type equal the fixture.
2. An event published in a committed transaction arrives exactly once.
3. An event published in a rolled-back transaction doesn't arrive within 2 s.
4. A send failure after commit is logged and doesn't propagate. This case is covered by the unit test
   `adapter/kafka/AfterCommitEventPublisherTest` rather than by stopping the shared Kafka container, which would
   break the other integration tests.
