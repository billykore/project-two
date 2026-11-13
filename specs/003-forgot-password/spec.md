# Feature Specification: Forgot Password

**Feature Branch**: `003-forgot-password`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "Develop fitur lupa password untuk user. User yang melakukan lupa password harus menerima
OTP untuk konfirmasi bahwa benar user tersebut yang memiliki akunnya. OTP bisa dikirim ke email atau nomor telepon.
Password yang baru tidak boleh 3 password sebelumnya dan harus memenuhi syarat password yang valid sama seperti di
registrasi"

## Clarifications

### Session 2026-11-13

- Q: Does "3 password sebelumnya" include the current password? → A: Yes. The last 3 passwords are the current one
  plus the 2 before it.
- Q: Should real email and SMS providers be integrated? → A: No. Delivery goes through replaceable channels; this
  feature ships a development delivery that makes the code available only to developers, and real providers come
  later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reset a forgotten password with a one-time code (Priority: P1)

A user who has forgotten their password asks to reset it by entering the email address or phone number on their
account. The system sends a one-time code (OTP) to that email address or phone number. The user enters the code
together with a new password; if the code is right and the password is acceptable, the password is replaced and the
user can log in with the new one. Receiving the code proves that the person resetting the password controls the
contact detail registered on the account.

**Why this priority**: today a user who forgets their password has no way back into their account except asking an
operator. Regaining access on their own is the whole point of the feature.

**Independent Test**: Register an account, request a reset by email, read the code delivered to that email, submit
it with a new valid password, then confirm login succeeds with the new password and fails with the old one. Repeat
with the phone number.

**Acceptance Scenarios**:

1. **Given** account `budi` has email `budi@example.com`, **When** someone requests a password reset for
   `budi@example.com`, **Then** a 6-digit code is sent to `budi@example.com` and the response says that, if an
   account exists for that address, a code has been sent.
2. **Given** account `budi` has phone `+6281234567890`, **When** someone requests a password reset for
   `+6281234567890`, **Then** a 6-digit code is sent by text message to `+6281234567890`, with the same response as
   in scenario 1.
3. **Given** a code was sent for `budi@example.com` less than 5 minutes ago, **When** the user submits that email
   address, the code, and the new password `newsecret99`, **Then** the password is changed and the response
   confirms it.
4. **Given** `budi`'s password was just reset to `newsecret99`, **When** they log in with `budi` / `newsecret99`,
   **Then** login succeeds; **and When** they log in with the old password, **Then** login fails like any wrong
   password.
5. **Given** a user has no access token, **When** they request or complete a reset, **Then** it is allowed (neither
   step requires being logged in).

---

### User Story 2 - Codes cannot be guessed, reused, or abused (Priority: P1)

Someone who does not control the account's email or phone must not be able to take over the account by guessing,
reusing, or flooding codes, nor learn which email addresses or phone numbers have accounts.

**Why this priority**: a reset flow is a second way into every account. Without these protections it is weaker than
the password it replaces, so it ships together with Story 1.

**Independent Test**: Request a code, then in turn: submit a wrong code 5 times and confirm the right code no longer
works; wait past expiry and confirm the code is refused; use a code once and confirm it cannot be used again;
request a new code and confirm the older one is refused; request a reset for an unknown email and confirm the
response is identical to that for a known one.

**Acceptance Scenarios**:

1. **Given** a valid code was sent, **When** a wrong code is submitted, **Then** the reset is refused with an
   "invalid or expired code" error and the password is unchanged.
2. **Given** 5 wrong codes have been submitted for the current code, **When** the correct code is then submitted,
   **Then** it is refused; the user must request a new code.
3. **Given** a code was sent more than 5 minutes ago, **When** it is submitted, **Then** it is refused with the same
   "invalid or expired code" error.
4. **Given** a code was already used to reset the password, **When** it is submitted again, **Then** it is refused.
5. **Given** a code was sent and then a new one was requested, **When** the first code is submitted, **Then** it is
   refused; only the newest code works.
6. **Given** no account has email `nobody@example.com`, **When** a reset is requested for it, **Then** the response
   is the same, in content and status, as for an existing account, and nothing is sent.
7. **Given** a code was requested for an account less than 60 seconds ago, **When** another code is requested for
   the same account, **Then** no new code is sent and the response is still the generic one.
8. **Given** 5 codes have been requested for an account within the last hour, **When** a sixth is requested,
   **Then** no code is sent until the hour has passed, and the response is still the generic one.

---

### User Story 3 - New password follows the registration rules and is not recently used (Priority: P2)

When choosing a new password, the user must meet the same rules as at registration and may not reuse any of their
last 3 passwords, so a reset cannot weaken the account or quietly put back a password that may have been
compromised.

**Why this priority**: the reset works without it (Story 1), but the user asked for both rules explicitly and they
protect accounts whose old password leaked.

**Independent Test**: For an account whose password has been changed twice through resets, submit valid codes with
each of the last 3 passwords and confirm each is refused; submit passwords breaking each registration rule and
confirm each is refused; confirm a valid, unused password is accepted.

**Acceptance Scenarios**:

1. **Given** a valid code, **When** the new password is `short12` (7 characters), **Then** the reset is refused, the
   error names the password field and says it must be at least 8 characters, and the password is unchanged.
2. **Given** a valid code for account `budi`, **When** the new password is `budi` (identical to the
   username), **Then** the reset is refused and the error names the password field.
3. **Given** `budi`'s current password is `secret123`, **When** they reset to `secret123`, **Then** the reset is
   refused with an error saying the password was used recently.
4. **Given** `budi` registered with `pass-one-1`, then reset to `pass-two-2`, then to `pass-three-3`, **When** they
   reset again to any of those three, **Then** the reset is refused as recently used; **When** they reset to
   `pass-four-4`, **Then** it succeeds, and afterwards `pass-one-1` is allowed again.
5. **Given** a reset is refused because of the new password (scenarios 1–4), **When** the user submits the same code
   with an acceptable password before it expires, **Then** the reset succeeds; a refused password does not use up
   the code or count as a wrong code.

---

### User Story 4 - Resetting signs out everywhere (Priority: P3)

After a password reset, every session the account had is ended, so someone who was logged in with the old
password loses access.

**Why this priority**: users often reset because they suspect someone else has their password. Valuable, but the
reset itself (Stories 1–3) delivers value without it.

**Independent Test**: Log in twice as `budi`, reset the password, and confirm both sessions are recorded as ended.

**Acceptance Scenarios**:

1. **Given** `budi` has two active sessions, **When** the password is reset, **Then** both sessions are ended.
2. **Given** `budi`'s password was reset, **When** they log in with the new password, **Then** a new session starts
   normally.

---

### Edge Cases

- **Email entered with different letter case or surrounding spaces** (` Budi@Example.com `): trimmed and matched
  case-insensitively, as at registration.
- **Phone number in local form or with spaces/dashes** (`0812…`, `+62 812-…`): rejected as an invalid phone number
  with the expected format, as at registration. Format errors are not account information, so revealing them is
  fine.
- **Identifier that is neither a valid email nor a valid phone number**: rejected as invalid input.
- **Username given instead of email or phone**: rejected as invalid input; reset is by contact detail only.
- **Account created before email and phone existed** (has neither): cannot reset by itself; the generic response is
  returned and nothing is sent. An operator must help.
- **Code submitted for a different email/phone than it was sent to**: refused as invalid; each code belongs to one
  account.
- **Code requested by email, completed with the account's phone number** (or vice versa): accepted; the code belongs
  to the account, and either registered contact detail identifies it.
- **Code with leading zeros** (`004213`): valid; codes are always exactly 6 digits.
- **Code with spaces or non-digits**: refused as invalid.
- **Two reset completions with the same code at the same moment**: exactly one succeeds.
- **Delivery to email or phone fails**: the user gets the same generic response; the failure is recorded for
  operators without the code.
- **New password with spaces, non-ASCII, or exactly 72 ASCII characters**: same outcomes as at registration.
- **Account with fewer than 3 known passwords** (for example only the original): only the known ones are blocked.
- **Logged-in user using the reset flow**: allowed; it behaves the same as for a logged-out user.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST let anyone without an access token request a password reset by giving an email address or
  a phone number.
- **FR-002**: System MUST validate the given identifier with the same email and phone format rules as registration
  (email trimmed and compared case-insensitively; phone as `+` followed by 8–15 digits) and reject malformed input
  with an error naming the field.
- **FR-003**: When the identifier matches an account, System MUST send a one-time code to that same identifier: by
  email for an email address, by text message for a phone number.
- **FR-004**: The response to a reset request MUST be identical, in status and content, whether or not an account
  matches, whether a code was sent, and whether a request limit applied.
- **FR-005**: A code MUST be 6 random digits, valid for 5 minutes, and usable for at most one successful reset.
- **FR-006**: Requesting a new code MUST invalidate any earlier unused code for that account.
- **FR-007**: After 5 wrong attempts against the current code, System MUST invalidate it.
- **FR-008**: System MUST send at most 1 code per account per 60 seconds and at most 5 per account per rolling hour.
- **FR-009**: System MUST let anyone without an access token complete a reset by giving the account's email address
  or phone number, the code, and a new password.
- **FR-010**: A wrong, expired, used, superseded, or invalidated code MUST be refused with one and the same error
  that does not reveal which of these applies, and the password MUST stay unchanged.
- **FR-011**: The new password MUST satisfy the registration password rules: 8–72 characters, at most 72 bytes in
  UTF-8, not identical to the username, and no composition rules.
- **FR-012**: The new password MUST NOT be equal to any of the account's last 3 passwords, counting the current
  password as one of the 3 (the current password plus the 2 before it).
- **FR-013**: A new password refused under FR-011 or FR-012 MUST be reported with an error naming the password field
  and the reason, MUST NOT count as a wrong code attempt, and MUST leave the code usable until it expires.
- **FR-014**: On a successful reset, System MUST replace the password, record the old one in the account's password
  history, use up the code, and confirm the reset without issuing an access token.
- **FR-015**: A successful reset MUST end all of the account's sessions.
- **FR-016**: Codes and passwords, including past passwords, MUST be stored only in one-way hashed form and MUST NOT
  be logged or returned in any response. The one exception is the development delivery of FR-020, which may log
  codes when it is explicitly enabled and MUST NOT be enabled in production.
- **FR-017**: A code MUST be accepted at most once even when the same code is submitted in two requests at the same
  moment.
- **FR-018**: Errors MUST use the same error format the rest of the system uses.
- **FR-019**: Login MUST remain unchanged apart from accepting the new password and refusing the old one.
- **FR-020**: Codes MUST be delivered through a replaceable email channel and a replaceable text-message channel.
  This feature ships a development delivery for both that makes the code available only to developers (never in a
  response to the requester); connecting real email and SMS providers is a later feature and MUST NOT require
  changing the reset rules.

### Key Entities

- **User account** (existing): identified for reset by its email address or phone number; holds the current hashed
  password.
- **Password reset code**: a one-time code issued to one account. Attributes: account, hashed code, channel it was
  sent through (email or phone), issue time, expiry time, number of wrong attempts, and when it was used or
  invalidated (if ever).
- **Password history entry**: a previous password of an account. Attributes: account, hashed password, and when it
  stopped being the current password. Only the most recent entries needed by FR-012 matter.
- **Session** (existing): ended by a successful reset.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who has forgotten their password can regain access (request code, receive it, reset, log in) in
  under 3 minutes without operator involvement.
- **SC-002**: 95% of codes are handed to the delivery channel within 1 second of the request (and, once real
  providers exist, reach the user within 1 minute).
- **SC-003**: 100% of reset attempts with a wrong, expired, used, superseded, or invalidated code leave the password
  unchanged.
- **SC-004**: An attacker can make at most 5 guesses per issued code and receive at most 5 codes per account per
  hour, so the chance of guessing a valid code in one hour is at most 25 in 1,000,000.
- **SC-005**: Reset-request responses for existing and non-existing accounts cannot be told apart by content or
  status.
- **SC-006**: 100% of new passwords that break a registration rule or match one of the last 3 passwords are
  refused with an error naming the reason.
- **SC-007**: No code or password, current or past, appears in plain form in stored data, logs, or responses.

## Assumptions

- The reset uses the email address and phone number captured at registration. They are still unverified; a
  successful reset through a contact detail does not mark it as verified, and verification remains a separate
  feature.
- The user picks the channel by what they enter: an email address means email, a phone number means text message.
- Unlike registration (which reveals that a contact detail is taken), the reset request does not reveal whether an
  account exists, because here a private channel to the real owner exists: the code itself.
- Code length (6 digits), lifetime (5 minutes), wrong-attempt limit (5), resend wait (60 seconds), and hourly limit
  (5) follow common industry practice and are configurable.
- Password history starts with this feature: for existing accounts only the current password is known, and older
  ones are added as passwords change.
- Ending sessions marks them as ended in the system's session records. Access tokens already issued remain valid
  until they expire, because login tokens are not checked against session records today; checking them is out of
  scope.
- No "your password was changed" notification is sent, and changing the password while logged in (knowing the old
  one) is out of scope; both are candidates for later features.
- CAPTCHA and rate limiting by network address are out of scope; limits are per account.
- Real email and SMS providers are out of scope. Until they exist, a user cannot receive a code outside a development
  environment, so the feature is complete for development and testing but not yet usable by end users in
  production.
- The development delivery may reveal codes to developers (for example in local logs). It MUST NOT be enabled in
  production, and SC-007 applies to every other part of the system.
