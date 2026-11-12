# Quickstart: Validate User Registration

**Feature**: [spec.md](./spec.md) | **Contract**: [contracts/http-api.md](./contracts/http-api.md)

## Prerequisites

- Java 17, and a Docker-compatible runtime (for Testcontainers).
- For the manual checks: the app running locally as described in the README (PostgreSQL, `.env` with `DB_*` and
  `APP_JWT_SECRET`). Flyway applies `V11` on startup.

## 1. Automated checks

```bash
./mvnw test
```

Expected: green, including:

| Test                         | Proves                                                                          |
|------------------------------|---------------------------------------------------------------------------------|
| `RegisterUserCommandTest`    | each rule and boundary (7/8 and 72/73 characters, 72/73 bytes), normalization, all errors at once |
| `UserTest`                   | `User.register` copies normalized fields and the hash, with no id               |
| `RegistrationServiceTest`    | conflicts per field and combined; hashing; nothing saved on conflict            |
| `JpaUserRepositoryIT`        | `exists*`, `add`, constraint names, concurrent username/email/phone races → exactly one success |
| `AuthControllerTest`         | 201 body without password, 400 `errors`, 409 `conflicts`, malformed body, ignored `role`, no token needed |
| `RegistrationFlowIT`         | register, then log in with the same credentials; a wrong password still 401s    |
| `ArchitectureTest`           | AR-01..AR-09 still pass                                                          |

## 2. Manual end-to-end (app running on :8080)

Register (expect `201` and the account without a password):

```bash
curl -i -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"username":"budi","password":"secret123","name":"  Budi Santoso ","email":"Budi@Example.com","phone":"+6281234567890"}'
```

Check: `name` is `"Budi Santoso"`, `email` is `"budi@example.com"`, and there is no `password` property.

Log in with the new account (expect `200` with `accessToken`):

```bash
curl -i -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"username":"budi","password":"secret123"}'
```

Repeat the registration with only the email case changed and a new username and phone (expect `409`,
`conflicts: ["email"]`):

```bash
curl -i -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"username":"budi2","password":"secret123","name":"Budi","email":"BUDI@example.com","phone":"+6281200000000"}'
```

Several invalid fields (expect `400` with three `errors`: password, email, phone):

```bash
curl -i -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"username":"ani","password":"short12","name":"Ani","email":"ani@","phone":"081234567890"}'
```

## 3. Data checks

```sql
SELECT username, name, email, phone, password LIKE '$2%' AS is_bcrypt FROM users WHERE username = 'budi';
```

Expected: one row, lowercase email, `is_bcrypt = true`. Grep the application log for `secret123`: there should be
no matches (SC-004).

## 4. Response time (SC-005)

"Normal load" here means registrations sent one at a time against a locally running app. Send 20 registrations with
unique values and print each response time:

```bash
for i in $(seq 1 20); do curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' -d "{\"username\":\"perf$i$RANDOM\",\"password\":\"secret123\",\"name\":\"Perf $i\",\"email\":\"perf$i$RANDOM@example.com\",\"phone\":\"+6281$RANDOM$i$RANDOM\"}"; done
```

Expected: every line starts with `201`, and at least 19 of the 20 times (95%) are under `2.0` seconds. Record the
slowest time in the pull request. If the target is missed, the likely cause is the password-hashing cost (research
R6), not the database.
