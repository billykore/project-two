# HTTP Contract: Password Reset

Both endpoints are public, because they are under `/auth/**` (FR-001, FR-009). A token sent anyway is ignored, and a
logged-in caller is treated the same as a logged-out one. All errors are `application/problem+json`. Both endpoints
are served by a new `api/controller/PasswordResetController` mapped to `/auth/password-reset`.

## `POST /auth/password-reset/request`

### Request

```json
{ "email": "budi@example.com" }
```

or

```json
{ "phone": "+6281234567890" }
```

Exactly one of `email` and `phone`. The rules are in
[data-model.md](../data-model.md#requestpasswordresetcommand-new-record). Unknown properties are ignored.

### 202 Accepted

The same body for every well-formed request: a known or unknown account, a code sent, delivery failed, or a limit
applied (FR-004, SC-005).

```json
{ "message": "If an account exists for this contact, a code has been sent to it." }
```

### 400 Invalid password reset

```json
{
  "type": "about:blank",
  "title": "Invalid password reset",
  "status": 400,
  "detail": "phone: must be in international format, e.g. +6281234567890",
  "instance": "/auth/password-reset/request",
  "errors": [
    { "field": "phone", "message": "must be in international format, e.g. +6281234567890" }
  ]
}
```

If both or neither property is given, the response lists `email` and `phone`, each with
`provide either email or phone`.

### 400 Malformed request

The body is missing or is not valid JSON. This is the existing response from `GlobalExceptionHandler`.

## `POST /auth/password-reset/confirm`

### Request

```json
{
  "email": "budi@example.com",
  "code": "004213",
  "newPassword": "newsecret99"
}
```

`email` or `phone` (exactly one), plus `code` and `newPassword`, all strings. The contact may be either one
registered on the account, whichever one the code was sent to. The rules are in
[data-model.md](../data-model.md#resetpasswordcommand-new-record).

### 204 No Content

The password was changed, the code was used up, and all of the account's sessions were ended. No body and no token;
the user logs in through the unchanged `POST /auth/login` (FR-014).

### 400 Invalid password reset

Input that breaks a rule, or a new password refused after the code was verified. The code is not used up, and a
refused password does not count as a wrong code attempt (FR-013).

```json
{
  "type": "about:blank",
  "title": "Invalid password reset",
  "status": 400,
  "detail": "newPassword: must not be one of your last 3 passwords",
  "instance": "/auth/password-reset/confirm",
  "errors": [
    { "field": "newPassword", "message": "must not be one of your last 3 passwords" }
  ]
}
```

Possible `newPassword` messages:

- the four `PasswordRules.formatError` messages;
- `must not be the same as the username`;
- `must not be one of your last 3 passwords`.

A `code` that is not 6 digits gets `must be 6 digits`.

Order of checks:

1. Input rules. All invalid fields are listed together.
2. The code (next section).
3. Username equality and history. These checks only run once the code has been verified, so the history is never
   revealed to someone without a valid code.

### 400 Invalid or expired code

The contact matches no account, or the account has no usable code, or the code is wrong, expired, used,
superseded, or invalidated after 5 wrong attempts. It is one response for all of these, and nothing in it says
which (FR-010). A wrong code against a usable code counts toward the 5-attempt limit.

```json
{
  "type": "about:blank",
  "title": "Invalid or expired code",
  "status": 400,
  "detail": "Invalid or expired code",
  "instance": "/auth/password-reset/confirm"
}
```

### 503 Busy

Another reset of the same account is in progress and held the account longer than the lock timeout (research R6).
Nothing was changed, the code was not counted, and the client may retry.

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 1
Content-Type: application/problem+json
```

```json
{
  "type": "about:blank",
  "title": "Password reset busy",
  "status": 503,
  "detail": "Another password reset for this account is in progress; try again",
  "instance": "/auth/password-reset/confirm"
}
```

The request endpoint never answers `503`: when it is busy, it returns the generic `202` (FR-004).

### 400 Malformed request

Same as for the request endpoint.

## Unchanged

- `POST /auth/login` and `POST /auth/register` keep their requests, responses, and errors. Registration's
  `errors[]` shape is the same as reset's.
- Login accepts the new password, and refuses the old one, immediately after a `204` (FR-019).
