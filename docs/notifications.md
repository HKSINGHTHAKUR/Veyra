# Notifications & Email Reliability (Phase Notifications)

## Lifecycle
```
PENDING → PROCESSING → SENT
             ↓
          FAILED → (retry) → PENDING → PROCESSING → SENT
                                    ↘ FAILED (max retries → permanently FAILED)
```
Statuses: `PENDING`, `PROCESSING`, `SENT`, `FAILED` (enum `NotificationStatus`). Types: `BOOKING_CONFIRMATION`, `APPOINTMENT_REMINDER`, `CANCELLATION`, `RESCHEDULE`, `DOCTOR_LEAVE`, `MEDICATION_REMINDER`.

## Architecture
```
Business operation (AppointmentService, DoctorLeaveService, ReminderScheduler)
  ↓ @Transactional → Notification PENDING (DB is source of truth)
  ↓
Background worker (NotificationProcessor @Scheduled fixedDelay 30s)
  ↓ claim (status transition PENDING/FAILED → PROCESSING, increment attemptCount, lastAttemptAt)
  ↓ EmailService.send(notification) (abstraction, not JavaMail details)
  ↓ SMTP (SmtpEmailService) / Fake (test)
  ↓ update to SENT (sentAt) or FAILED (failureReason, lastAttemptAt)
```
Business services depend on `EmailService` abstraction, not `JavaMailSender`. No `emailService.send()` inside booking transaction – transaction commits `PENDING` first, then worker sends.

## Email Abstraction
`EmailService { void send(Notification) throws EmailException }`
- `SmtpEmailService` (`@Profile !test`): uses `JavaMailSender`, `MimeMessageHelper`, `MAIL_*` env, logs `INFO` on success.
- `FakeEmailService` (`@Profile test`, `@Primary`): records `sent` list, configurable `shouldFail` / `failNext(N)` for deterministic tests, no SMTP.
`EmailTemplateService` generates subjects/bodies for each type (confirmation, cancellation, reschedule with old/new time, reminder 24h, doctor-leave with date/appointment). Templates kept as Java methods + `resources/templates/email/*.txt` for maintainability (no HTML builder).

## Scheduler & Claiming
`NotificationProcessor.processPending()` (`@Scheduled fixedDelay 30s`, `worker-batch-size 20`):
1. `findByStatus(PENDING)` (limit batch) → for each try claim `PENDING→PROCESSING` via `saveAndFlush` (single worker: simple save; production would use atomic `UPDATE WHERE status=:expected` to prevent duplicate with concurrent workers).
2. `findRetryable(FAILED, maxRetries=3)` → filter `isEligibleForRetry` (backoff 1,5,15 min for attempt 1,2,3) → claim `FAILED→PROCESSING`.
3. Send via `EmailService`, on success `SENT`, on `EmailException` `FAILED` with truncated `failureReason` (≤500), `lastAttemptAt=now`.

`ReminderScheduler.createReminders()` (`@Scheduled fixedDelay 60s`): finds `BOOKED/RESCHEDULED` appointments where `appointmentStart ∈ [now+24h -30m, now+24h +30m]` via `findByAppointmentStartBetweenAndStatusIn`, skips if `existsReminderForAppointment` already true (idempotent), creates `APPOINTMENT_REMINDER` PENDING via `NotificationService`.

Both schedulers are idempotent: repeated runs do not duplicate reminders (existence check) or double-send same notification (status transition).

## Retries & Backoff
- `max-retries=3` (`app.notifications.max-retries`), `worker-batch-size`, `worker-interval-ms`, `reminder-window-minutes` configurable via `application.yml` / env `NOTIFICATION_*`.
- On failure: `attemptCount++`, `status=FAILED`, `lastAttemptAt=now`, `failureReason` truncated. Next retry only if `attemptCount < max` and `now > lastAttemptAt + backoff`.
- Backoff: attempt 1→1m, 2→5m, 3→15m. Temporary SMTP failure recovers; permanent failure stops after max.

## Failure Logging & Isolation
- Logs `WARN` with `notificationId, type, attemptCount, reason` (no passwords/JWT/clinical data).
- `AppointmentService` booking/cancel/reschedule transactions only create `PENDING` notifications; `EmailException` does **not** rollback appointment (verified by tests: booking succeeds even if `FakeEmailService` fails, appointment remains `BOOKED`, notification becomes `FAILED` retryable).
- `DoctorLeaveService.addLeave` creates `DOCTOR_LEAVE` notifications for future `BOOKED/RESCHEDULED` appointments on that leave date (past ignored, unaffected dates ignored, duplicate leave notifications prevented via `existsDoctorLeaveForAppointment`). Does not delete appointments.

## Doctor-Leave Notifications
When admin adds leave (`POST /api/admin/doctors/{id}/leaves`):
- Compute `dayStart/dayEnd` (UTC) for leaveDate
- Find `appointments` where `doctorId` matches, `appointmentStart ∈ [dayStart, dayEnd)`, `status ∈ BOOKED/RESCHEDULED`, `appointmentStart > now`
- For each affected, `createNotification(patient.user, appointmentId, DOCTOR_LEAVE, subject, body)` where body explains doctor unavailable, appointment time, and to reschedule via frontend. Past/unaffected not notified. Appointment state unchanged.

## 24-Hour Reminders
`ReminderScheduler` creates `APPOINTMENT_REMINDER` approx 24h before `appointmentStart` (±30m window). Duplicate prevented via `existsByAppointmentIdAndType`.

## API
- `GET /api/notifications?status=&type=` – returns own notifications (ownership via `CurrentUserService.getCurrentUserId()`; never trusts frontend userId). Supports filtering by `status`/`type`. Patients/doctors see own; admin sees own (could be extended to all). No `PATCH /read` – entity has no read flag.
- `GET /api/notifications/{id}` – ownership check (owner or ADMIN else 403).

## Templates
`src/main/resources/templates/email/{confirmation,reminder,cancellation,reschedule,doctor-leave}.txt` + `EmailTemplateService` methods (e.g., `confirmationSubject`, `confirmationBody` with `DateTimeFormatter` UTC, frontend URL). No huge HTML in services.

## Database
`notifications` (V1) already has `user_id, appointment_id, type, status, subject, body, attemptCount, lastAttemptAt, sentAt, failureReason, createdAt/updatedAt`. No V4 needed (fields sufficient). `NotificationRepository` adds `existsByAppointmentIdAndType`, `claim` (atomic update, documented for production), `findRetryable`.

## Testing
Deterministic via `FakeEmailService`, no SMTP needed. Tests call `notificationProcessor.processForTest()` and `reminderScheduler.createRemindersForTest(fixedNow)` directly, fixed timestamps, no waiting for real scheduler.

## Config
`application.yml` `app.notifications.*` + `MAIL_*` (SMTP). `application-test.yml` uses `FakeEmailService`, `worker-interval 600s` to avoid auto-run during tests. `backend/.env.example` documents `NOTIFICATION_*` vars. No secrets hardcoded.

## Why Not Inside Transactions
`confirmHold` does `saveAppointment + save Notification PENDING + publishEvent` then commits; worker sends email afterwards. If SMTP down, appointment stays `BOOKED`; notification stays `PENDING/FAILED` for retry. This is verified by `NotificationTest` failure-isolation tests.
