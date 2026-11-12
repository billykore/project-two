# Feature Specification: User Registration

**Feature Branch**: `002-user-registration`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Develop an user registration feature. User must provide unique username, secure password with minimum length of 8, and name"

## Clarifications

### Session 2026-11-12

- Directive: User also must provide unique phone number and email → Email address and phone number added as
  required fields, each unique across all accounts.
- Q: In what format is the phone number accepted and compared for uniqueness? → A: International form with a
  country code (`+` followed by 8–15 digits, e.g. `+6281234567890`), stored and compared exactly in that form.
- Q: Must the user verify ownership of the email address or phone number before the account can log in? → A: No.
  The account is usable immediately; email and phone are stored unverified, and verification is a later feature.
- Q: Should login also accept the email address or phone number as the identifier? → A: No. Login stays username +
  password; email and phone are contact details only.
- Q: May a conflict error reveal that a given email address or phone number already has an account? → A: Yes. The
  conflict names each field already in use (username, email, or phone).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create an account and log in (Priority: P1)

A new person who has no account yet signs up by giving a username, a password, their name, their email address, and
their phone number. The account is
created right away, and they can then log in with that username and password using the existing login, without
anyone having to insert them into the system by hand.

**Why this priority**: today the only way to get an account is for an operator to add one directly to the data
store. Self-service sign-up is the core value of the feature; without it nothing else here matters.

**Independent Test**: Register a new account with valid details, confirm the response says the account was created
and shows the username, name, email address, and phone number (but no password), then log in with the same username and password and receive an
access token.

**Acceptance Scenarios**:

1. **Given** no account uses username `budi`, email `budi@example.com`, or phone `+6281234567890`, **When** a
   visitor registers with username `budi`, password `secret123`, name `Budi Santoso`, email `budi@example.com`, and
   phone `+6281234567890`, **Then** the account is created and the response contains the new account's
   identifier, username, name, email address, and phone number, and does not contain the password in any form.
2. **Given** the account `budi` was just registered with password `secret123`, **When** the visitor logs in with
   `budi` / `secret123`, **Then** login succeeds and returns an access token.
3. **Given** the account `budi` was just registered, **When** someone logs in with `budi` and a different password,
   **Then** login fails exactly as it does for any other wrong password.
4. **Given** a visitor has no access token, **When** they register, **Then** registration is allowed (it does not
   require being logged in).

---

### User Story 2 - Usernames, email addresses, and phone numbers stay unique (Priority: P2)

A visitor tries to register with a username, email address, or phone number that already belongs to someone else.
The system refuses, tells them which of these is already in use, and leaves the existing account untouched, so
nobody can take over or shadow another person's account or contact details.

**Why this priority**: uniqueness is what makes a username usable as a login identity and an email address or phone
number usable to reach exactly one person. It is required for the feature to be safe, but it only matters once
accounts can be created (Story 1).

**Independent Test**: Register `budi`, then try to register again reusing, in turn, the username, the email address,
and the phone number (with every other field new); confirm each attempt is rejected as a conflict naming the reused
field, and that `budi` can still log in with the original password.

**Acceptance Scenarios**:

1. **Given** an account with username `budi` exists, **When** a visitor registers with username `budi`, **Then**
   registration is rejected with a "username already taken" conflict, and no account is created or changed.
2. **Given** an account with email `budi@example.com` exists, **When** a visitor registers with email
   `Budi@Example.com` and an unused username and phone, **Then** registration is rejected with an "email already
   registered" conflict.
3. **Given** an account with phone `+6281234567890` exists, **When** a visitor registers with that phone and an
   unused username and email, **Then** registration is rejected with a "phone number already registered" conflict.
4. **Given** an account with username `budi` exists, **When** any of the rejected registrations above has
   happened, **Then** `budi` can still log in with their original password and their details are unchanged.
5. **Given** no account uses username `ani`, **When** two visitors register `ani` at the same moment, **Then**
   exactly one registration succeeds and the other is rejected as "username already taken". The same holds for two
   simultaneous registrations with the same email address or the same phone number.

---

### User Story 3 - Clear feedback on invalid details (Priority: P3)

A visitor submits details that don't meet the rules: a password that is too short, a missing name, or a username
with characters that aren't allowed. The system rejects the request without creating anything and tells them which
field is wrong and why, so they can fix it and try again.

**Why this priority**: rules on the password and username are required (Story 1 enforces them), but precise,
per-field feedback is a usability improvement on top of a plain rejection.

**Independent Test**: Submit registrations that break each rule one at a time and confirm each is rejected, names
the offending field with a reason, and creates no account.

**Acceptance Scenarios**:

1. **Given** a visitor, **When** they register with password `short12` (7 characters), **Then** registration is
   rejected, the error names the password field and says it must be at least 8 characters, and no account exists
   for that username.
2. **Given** a visitor, **When** they register with an empty or whitespace-only name, **Then** registration is
   rejected and the error names the name field.
3. **Given** a visitor, **When** they register with username `Budi!`, **Then** registration is rejected and the
   error names the username field and the allowed characters.
4. **Given** a visitor, **When** they register with a password equal to their username, **Then** registration is
   rejected and the error names the password field.
5. **Given** a visitor, **When** they register with email `budi@` or `budi.example.com`, **Then** registration is
   rejected and the error names the email field.
6. **Given** a visitor, **When** they register with phone `081234567890` (no country code) or `+62 812-3456-7890`
   (spaces and dashes), **Then** registration is rejected and the error names the phone field and the expected
   international format.
7. **Given** a visitor, **When** they submit several invalid fields at once, **Then** the error lists every invalid
   field, not only the first.

---

### Edge Cases

- **Username differing only in letter case** (`Budi` vs `budi`): usernames are lowercase-only, so `Budi` is
  rejected as invalid characters rather than creating a look-alike second account.
- **Leading or trailing spaces in the username**: rejected as invalid characters; the system does not silently
  trim and then collide with an existing account.
- **Leading or trailing spaces in the name**: trimmed before saving; a name that is empty after trimming is
  rejected.
- **Spaces in the password**: allowed and kept exactly as typed; the password is never trimmed or altered.
- **Password at exactly 8 characters**: accepted. At 7: rejected.
- **Password longer than 72 characters**: rejected with a clear message, rather than being silently cut short
  (longer passwords could otherwise be stored in a way that ignores the extra characters).
- **Password of 72 characters or fewer but more than 72 bytes in UTF-8** (for example 40 × `é`, 80 bytes):
  rejected with a message explaining that non-ASCII characters count as more than one. An all-ASCII password of 72
  characters is accepted.
- **Non-ASCII characters in the name** (`Siti Nurhaliza`, `José`, `王伟`): accepted.
- **Email differing only in letter case** (`Budi@Example.com` vs `budi@example.com`): treated as the same address;
  email addresses are trimmed and stored in lowercase, so the second registration is a conflict.
- **Email with leading or trailing spaces**: trimmed before validation and the uniqueness check.
- **Phone number written in local form** (`0812…`) **or with spaces, dashes, or brackets**: rejected with a message
  showing the expected form (`+6281234567890`); the system does not reformat it.
- **Same phone number registered from different countries' formats**: cannot happen, because only one form is
  accepted.
- **Two registrations colliding on different fields** (one reuses a username, the other an email): each is rejected
  for its own conflicting field.
- **A registration reusing several taken values at once**: rejected; the conflict names every field already in use.
- **Missing fields**: rejected as an invalid registration that lists each missing field as required; nothing is
  created.
- **Missing or malformed request body** (not valid JSON): rejected as a malformed request; nothing is created.
- **Registering while already logged in**: allowed and creates a separate account; the existing login is not
  affected.
- **Unexpected extra fields** (for example an attempt to set a role or driver status): ignored; a registered
  account is always an ordinary customer account.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST let a visitor without an access token create an account by providing a username, a
  password, a name, an email address, and a phone number. All five are required.
- **FR-002**: System MUST require the username to be 3–50 characters long and contain only lowercase letters
  (a–z), digits (0–9), dots (`.`), underscores (`_`), and hyphens (`-`).
- **FR-003**: System MUST reject a registration whose username already belongs to an existing account, report it as
  a conflict ("username already taken"), and leave the existing account unchanged.
- **FR-004**: System MUST guarantee username uniqueness even when two registrations for the same username arrive at
  the same time: exactly one succeeds.
- **FR-005**: System MUST require the password to be at least 8 and at most 72 characters long, and at most 72
  bytes when encoded as UTF-8.
- **FR-006**: System MUST reject a password that is identical to the chosen username.
- **FR-007**: System MUST NOT impose character-class composition rules (such as "must contain a digit and a
  symbol") on the password beyond FR-005 and FR-006.
- **FR-008**: System MUST require a name of 1–100 characters after trimming leading and trailing whitespace, and
  MUST store the trimmed name.
- **FR-009**: System MUST store passwords only in a one-way, salted, deliberately slow hashed form, the same form the
  existing login already verifies; the plain password MUST NOT be stored, logged, or returned.
- **FR-010**: On success, System MUST confirm the account was created and return the account's identifier,
  username, name, email address, and phone number, and nothing about the password.
- **FR-011**: A newly registered account MUST be able to log in immediately through the existing login with the
  registered username and password.
- **FR-012**: Registration MUST NOT log the visitor in or issue an access token; logging in remains a separate step.
- **FR-013**: Every registered account MUST be an ordinary customer account; registration MUST NOT grant driver
  status or any other elevated capability, regardless of what the request contains.
- **FR-014**: When input is invalid, System MUST reject the whole request, create nothing, and return an error that
  names each invalid field with a human-readable reason, in the same error format the rest of the system uses.
- **FR-015**: System MUST record when each account was created.
- **FR-016**: System MUST require a syntactically valid email address of at most 254 characters, trim it, and
  store it in lowercase.
- **FR-017**: System MUST reject a registration whose email address (compared case-insensitively) already belongs
  to an existing account, reporting it as a conflict ("email already registered").
- **FR-018**: System MUST require the phone number in international form: a `+` followed by 8–15 digits, with no
  spaces or other characters, and MUST store it exactly as given.
- **FR-019**: System MUST reject a registration whose phone number already belongs to an existing account,
  reporting it as a conflict ("phone number already registered").
- **FR-020**: Uniqueness of email address and phone number MUST hold under simultaneous registrations, as FR-004
  requires for usernames. When a registration conflicts on more than one field, the conflict MUST name each of them;
  the one exception is a simultaneous race, where the rejected registration MUST name at least one conflicting field.
- **FR-021**: System MUST NOT require the email address or phone number to be verified before the account can log
  in; both are stored as unverified contact details.
- **FR-022**: Login MUST remain unchanged: it identifies the account by username only. Logging in with an email
  address or phone number in place of the username MUST fail like any unknown username.

### Key Entities

- **User account**: a person who can log in. Attributes: system-assigned identifier, username (unique login
  identity, lowercase), name (display name, up to 100 characters), email address (unique, lowercase), phone number
(unique, international form), hashed password, and creation time. Accounts
  created by registration are customers; becoming a driver is a separate, later process.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can go from having no account to holding an access token (register, then log in) in under
  1 minute, without operator involvement.
- **SC-002**: 100% of registration attempts with an already-taken username, email address, or phone number are
  rejected, including attempts made simultaneously, and no existing account is ever modified by a registration.
- **SC-003**: 100% of registration attempts that break any rule in FR-002, FR-005, FR-006, FR-008, FR-016, or
  FR-018 are rejected
  with an error naming every invalid field, and create no account.
- **SC-004**: No plain-text password appears in stored data, application logs, or any response, verified across all
  registration outcomes (success, conflict, invalid input).
- **SC-005**: Registration responds to the visitor within 2 seconds for 95% of attempts under normal load,
  meaning registrations arriving one at a time.
- **SC-006**: Operators no longer need to add accounts by hand: the README's manual account-creation step is
  replaced by the registration flow.

## Assumptions

- Registration is self-service and public, in line with the existing rule that only the authentication area is
  reachable without a token.
- Usernames are lowercase-only, which makes uniqueness unambiguous without case-folding rules, and keeps existing
  login behaviour (exact username match) correct. Existing accounts are not migrated or re-validated against the
  new username rules.
- "Secure password" follows current industry guidance (NIST SP 800-63B): length matters more than composition, so
  the rules are a minimum of 8 characters, a maximum of 72 characters and 72 UTF-8 bytes (the password hashing
  already used by login reads only the first 72 bytes), and not equal to the username. Checking passwords against breached-password lists is out of scope for
  this version.
- Revealing that a username, email address, or phone number is already in use is accepted for usability: without
  verification there is no private channel to tell the real owner instead. This lets anyone check whether an email
  or phone has an account; limiting that through rate limiting is left to a later feature.
- Email and phone verification (sending codes or links, blocking login until confirmed) is out of scope and planned
  as a separate feature. Because contact details are unverified, someone can register with an address or number
  they don't own and so block its real owner from registering with it; this risk is accepted until verification
  exists.
- CAPTCHA, rate limiting of registration attempts, password
  reset, profile editing, account deletion, and driver onboarding are out of scope.
- No event is published when an account is registered; nothing in the system consumes one yet.
- The existing account store has a name, a unique username, a hashed password, and a creation time, but no email
  address or phone number; these two attributes are new. Accounts created before this feature have neither, so
  they stay optional for existing accounts and required for every new registration.
