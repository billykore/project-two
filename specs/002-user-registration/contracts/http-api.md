# HTTP Contract: `POST /auth/register`

Public endpoint: no `Authorization` header is needed, since it is under `/auth/**` (FR-001). A token sent anyway
is ignored. All errors are `application/problem+json`.

## Request

```http
POST /auth/register
Content-Type: application/json
```

```json
{
  "username": "budi",
  "password": "secret123",
  "name": "Budi Santoso",
  "email": "budi@example.com",
  "phone": "+6281234567890"
}
```

All five properties are required strings. Rules are in [data-model.md](../data-model.md#registerusercommand-value-object-corevalueobject-new).
Unknown properties (for example `"role"`) are ignored.

## 201 Created

```json
{
  "id": 42,
  "username": "budi",
  "name": "Budi Santoso",
  "email": "budi@example.com",
  "phone": "+6281234567890"
}
```

`name` is the trimmed value and `email` the lowercased value as stored. The body never contains `password` or a
token (FR-010, FR-012). There is no `Location` header, because no `GET /users/{id}` exists.

## 400 Invalid registration

Any rule violation. Every invalid field is listed once (FR-014).

```json
{
  "type": "about:blank",
  "title": "Invalid registration",
  "status": 400,
  "detail": "password: must be at least 8 characters; phone: must be in international format, e.g. +6281234567890",
  "instance": "/auth/register",
  "errors": [
    { "field": "password", "message": "must be at least 8 characters" },
    { "field": "phone", "message": "must be in international format, e.g. +6281234567890" }
  ]
}
```

## 400 Malformed request

The body is missing or is not valid JSON (applies to every endpoint, research R10).

```json
{
  "type": "about:blank",
  "title": "Malformed request",
  "status": 400,
  "detail": "Request body is missing or is not valid JSON",
  "instance": "/auth/register"
}
```

## 409 Registration conflict

One or more of `username`, `email`, `phone` already belongs to an account. Nothing is created or changed.

```json
{
  "type": "about:blank",
  "title": "Registration conflict",
  "status": 409,
  "detail": "username already taken; email already registered",
  "instance": "/auth/register",
  "conflicts": ["username", "email"]
}
```

`conflicts` is ordered `username`, `email`, `phone`. In a simultaneous-registration race it may name only one
field (research R4).

## Unchanged

`POST /auth/login` keeps its request, response, and errors. It identifies accounts by `username` only; an email
or phone sent as `username` fails with the existing `401 Invalid credentials` (FR-022).
