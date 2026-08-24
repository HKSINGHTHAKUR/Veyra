# Google Calendar Integration (OAuth 2.0)

## Architecture
```
CalendarService (business: OAuth connect/status/disconnect, token refresh)
        ↓
GoogleCalendarClient (interface – no SDK dependency)
   ├── HttpGoogleCalendarClient (@Profile !test): Google OAuth 2.0 + Calendar REST v3 via RestTemplate
   └── FakeGoogleCalendarClient  (@Profile test, @Primary): in-memory, deterministic, configurable failures

CalendarSyncService (per-appointment sync orchestration + background worker logic)
        ↑ enqueueForParticipants(appointment)  ← DB-only, safe inside booking transactions
CalendarSyncWorker (@Scheduled fixedDelay worker-interval-ms) → processPending()
```
AppointmentService never calls Google directly. It only enqueues `CalendarSync` rows inside its transaction; the actual API calls happen after commit in the worker. A Google outage can never break booking/cancel/reschedule.

## OAuth Flow
```
GET /api/calendar/connect            → {authorizationUrl} (client_id, redirect_uri, scope=calendar.events,
                                       access_type=offline, prompt=consent, state=<authenticated userId>)
User consents at Google
GET /api/calendar/callback?code&state → state MUST equal authenticated userId (CSRF/mix-up guard)
                                       → exchangeCode(code) → store minimum tokens → {"status":"CONNECTED"}
GET /api/calendar/status             → {connected: bool, userId}
POST /api/calendar/disconnect        → deletes credential row
```
Ownership derives exclusively from `CurrentUserService`; request bodies/state can never target another user.

## Token Handling
`calendar_credentials` table (V5): one row per user (`uq_credentials_user`) storing **only** the minimum required: `refresh_token`, current `access_token`, `access_token_expires_at`.
- Tokens are NEVER returned through any API and NEVER logged.
- `CalendarService.getValidAccessToken(user)` transparently refreshes expired tokens via the client before API calls.
- Disconnect deletes the stored credentials.
Limitation (documented): tokens are stored as-is (no envelope encryption); acceptable for an assignment, would use a KMS/secret store in production.

## Event Content (privacy)
Title: `Appointment – Dr. <LastName> (<Specialisation>)`. Description: scheduling facts only (who/when/duration).
**Never** included: symptoms, AI summaries, prescriptions, passwords, internal IDs.

## Event Creation Flow
1. `confirmHold` commits appointment (BOOKED) + notification + `CalendarSync PENDING` rows for each participant who has connected their calendar (DB-only inside tx).
2. `CalendarSyncWorker` claims the row, resolves a valid access token, calls `client.createEvent`.
3. Success → `google_event_id` stored, status `SYNCED`.

## Reschedule Flow
Reschedule succeeds first (same-row UPDATE guarded by `uq_appointment_active`). `enqueueForParticipants` then flips existing sync rows back to `PENDING`. Worker sees `eventId != null` + non-cancelled appointment → **updates** the same event to the new time → `SYNCED`. No new event is created. If Google update fails: sync `FAILED`, appointment stays rescheduled.

## Cancellation Flow
Cancel sets appointment `CANCELLED`, re-enqueues syncs. Worker sees cancelled appointment:
- eventId exists → `deleteEvent` → status `CANCELLED` (enum value reused for "event removed")
- no eventId → straight to `CANCELLED`
If deletion fails: sync `FAILED`, appointment remains `CANCELLED` (never resurrected).

## Failure Isolation
Google is called ONLY by `CalendarSyncWorker` after business transactions commit. Outcomes:
- Google down during booking → appointment BOOKED, sync FAILED (retryable)
- Google down during cancel → appointment CANCELLED, sync FAILED
- Google down during reschedule → appointment stays moved, sync FAILED
Identical principle to email/AI phases.

## Retries & Idempotency
- Bounded: `app.google.calendar.max-retries` (default 3), backoff 1/5/15 min via `last_sync_attempt_at`, batch size + worker interval configurable.
- Idempotent by DB state (no duplicate events):
  - `SYNCED` rows are never reprocessed
  - no eventId → create once; eventId stored on success
  - eventId present → update (not create)
  - cancelled appointment → delete path
- Claiming increments `attempt_count` and stamps `last_sync_attempt_at` before any external call; concurrent workers cannot double-create because creation happens exactly when `eventId IS NULL` and the row transitions out of PENDING on completion.

## Timezone Decision
All event times are UTC Instants rendered as RFC-3339 `'Z'` strings with `timeZone: "UTC"`. One deliberate decision inside `GoogleCalendarClient.CalendarEventDetails` – matches the project-wide UTC model; no scattered conversions.

## Testing (14 tests, no network)
`FakeGoogleCalendarClient`: in-memory event map, per-op failure switches (`failCreate/failUpdate/failDelete`), counters for create/update/delete, deleted-id log. Tests drive the real HTTP endpoints for OAuth and call `CalendarSyncWorker.processForTest()` deterministically. Coverage: URL generation, callback+storage, token non-exposure, state mismatch rejection, create→SYNCED, create failure isolation, reschedule update (same eventId), update failure isolation, cancellation deletion, delete failure isolation, idempotency across repeated runs, bounded retries, unauthenticated callback, unconnected users unaffected.
