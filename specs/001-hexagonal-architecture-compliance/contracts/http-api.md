# Contract: HTTP API

This feature adds no endpoints and changes none of the request or response fields (FR-019). This file records the
contract that must stay identical, and the one deliberate change.

## Unchanged endpoints

| Method & path | Auth | Request | Success | Documented errors (Problem Details) |
|---------------|------|---------|---------|-------------------------------------|
| `POST /auth/login` | none | `{username, password}` | `200` `LoginResponse` | `400` validation; `401` invalid credentials |
| `GET /hello/{username}` | JWT | — | `200` `HelloResponse` | `401` no or invalid token; `404` unknown user |
| `POST /trips` | JWT | `{serviceType: "MyRide"\|"MyCar", pickup{latitude,longitude,address}, destination{…}, paymentMethod: CASH\|CREDIT_CARD\|QRIS}` | `201` `TripResponse`, `Location: /trips/{id}` | `400` validation; `400` "Invalid trip" (unknown service type, same pickup and destination); `401`; `409` "Active trip exists" (+ `activeTripId` when known) |
| `POST /driver/offers/{tripId}/accept` | JWT | — | `200` `TripResponse` | `401`; `409` trip not awaiting a driver; others unchanged |

The details that must not change:
- `serviceType` stays case-sensitive. `MYRIDE`, `myride` and unknown names give `400` with the same detail text:
  `Unknown service type, expected MyRide or MyCar`.
- The order of checks on `POST /trips` stays the same:
  1. Bean Validation (`400`)
  2. unknown service type (`400`)
  3. existing active trip (`409`)
  4. same pickup and destination (`400`)
  5. concurrent duplicate (`409`)
- `TripResponse` keeps the fields `id`, `status` and `serviceType`, with the same values. `serviceType` is the numeric
  service-type id: `1` for MyRide, `2` for MyCar.

## Deliberate change: unusable caller identity → 401

| Case | Before | After |
|------|--------|-------|
| Valid JWT without a `sub` claim on `POST /trips` | The trip was created for customer `0`, which then failed a foreign key (500), or it succeeded if a user `0` existed. | `401` Problem Details, title `Unauthenticated`, header `WWW-Authenticate: Bearer` |
| Valid JWT without `sub` on `POST /driver/offers/{id}/accept` | `assert` (disabled by default) followed by `NumberFormatException` from `Long.parseLong(null)`, giving 500 | `401` as above |
| Valid JWT whose `sub` isn't a number | `NumberFormatException`, giving 500 | `401` as above |

Tokens issued by this application always carry a numeric `sub`, so regular clients see no difference.

The `401` body matches what the existing authentication entry point returns for a missing or invalid token:

```json
{
  "type": "about:blank",
  "title": "Unauthenticated",
  "status": 401,
  "detail": "Token is missing, invalid, or expired",
  "instance": "/trips"
}
```

## Verified by

`@WebMvcTest` tests for `TripController` and `DriverOfferController` with mocked use-case ports:
- the success status and body;
- each documented error mapping;
- the three 401 cases above.

These are the minimum Principle III requires for the controllers this feature changes. Web-layer tests for the
untouched `AuthController` and `HelloController` stay out of scope (spec FR-022).
