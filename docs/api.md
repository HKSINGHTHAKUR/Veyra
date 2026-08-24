# API

Base: `http://localhost:8080` – JSON, JWT Bearer (`Authorization: Bearer <token>`). Error shape `ErrorResponse{status, message, fieldErrors?}`.

## Auth
| Method | Path | Auth | Body | Response |
|--------|------|------|------|----------|
| POST | /api/auth/register | public | {firstName,lastName,email,password,phone?} | 201 {token,email,role:PATIENT,patientId} / 409 dup / 400 validation |
| POST | /api/auth/login | public | {email,password} | 200 {token,email,role,patientId/doctorId} / 401 |

## Admin – Doctor Management (`@PreAuthorize hasRole('ADMIN')`)
| Method | Path | Response |
|--------|------|----------|
| POST | /api/admin/doctors | 201 DoctorResponse / 409 email dup |
| GET | /api/admin/doctors | 200 List<DoctorResponse> |
| GET | /api/admin/doctors/{id} | 200 DoctorResponse / 404 |
| PATCH | /api/admin/doctors/{id} | 200 DoctorResponse |
| PATCH | /api/admin/doctors/{id}/deactivate | 200 {active:false} |
| PATCH | /api/admin/doctors/{id}/activate | 200 {active:true} |
| POST | /api/admin/doctors/{id}/working-hours | 201 WorkingHoursResponse / 400 invalid times / 409 dup day |
| PUT | /api/admin/doctors/{id}/working-hours/{whId} | 200 |
| GET | /api/admin/doctors/{id}/working-hours | 200 List |
| DELETE | /api/admin/doctors/{id}/working-hours/{whId} | 204 |
| POST | /api/admin/doctors/{id}/leaves | 201 LeaveResponse / 409 dup date |
| GET | /api/admin/doctors/{id}/leaves | 200 List |
| DELETE | /api/admin/doctors/{id}/leaves/{leaveId} | 204 |

`DoctorResponse`: {id,userId,email,firstName,lastName,phone,specialisation,bio,consultationFee,active,createdAt} – never passwordHash.

## Doctor Discovery & Availability (authenticated)
| Method | Path | Query | Response |
|--------|------|-------|----------|
| GET | /api/doctors | ?specialisation=Cardio (contains, case-insensitive) | 200 List<DoctorSummaryResponse> (active only, no email) |
| GET | /api/doctors/{id} |  | 200 DoctorSummaryResponse / 404 if inactive |
| GET | /api/doctors/{id}/availability | ?date=YYYY-MM-DD | 200 AvailabilityResponse{doctorId,date,slots:[{slotStart,slotEnd,available}]} – past slots excluded, leave/workingHours respected |

## Booking – Holds (PATIENT)
| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | /api/appointments/hold | {doctorId, slotStart:Instant(UTC)} | 201 HoldResponse{holdId,doctorId,patientId,slotStart,slotEnd,expiresAt,status:ACTIVE} / 400 past/inactive / 409 invalid grid/leave/booked/held / 401/403 |
| GET | /api/appointments/holds/{holdId} |  | 200 HoldResponse (owner patient or ADMIN) / 403 not owner / 404 unknown hold |

`GET /holds/{holdId}` enforces ownership: only the patient who created the hold (via `CurrentUserService.getCurrentPatient()`) or an ADMIN may view; other patients receive `403 Forbidden` (no booking data leaked). `isAuthenticated()` alone is insufficient – service checks ownership.

`slotEnd` is derived server-side from working-hours duration – not trusted from client. Hold duration from `app.slot.hold-duration-minutes` (5 test, 10 prod).

## Symptoms (PATIENT, owns hold)
| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | /api/appointments/{holdId}/symptoms | {symptoms:string 1..5000} | 201 SymptomResponse / 404 not owner / 409 expired/not active |

## Confirmation (PATIENT, owns hold)
| Method | Path | Response |
|--------|------|----------|
| POST | /api/appointments/{holdId}/confirm | 201 AppointmentResponse / 404 not owner / 409 expired/already converted/slot booked concurrently |

Creates Appointment(BOOKED), converts hold → CONVERTED, links symptom, creates Notification(PENDING, BOOKING_CONFIRMATION).

## Appointments
| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | /api/appointments/{id} | owner patient / assigned doctor / admin | 200 AppointmentResponse{+symptomText} / 403 otherwise |
| GET | /api/patient/appointments | PATIENT | 200 List<AppointmentResponse> (own, ordered bookedAt desc) |
| GET | /api/doctor/appointments | DOCTOR | 200 List<AppointmentResponse> (own, ordered start asc) |
| POST | /api/appointments/{id}/cancel | owner patient, assigned doctor, or ADMIN (verified via `CurrentUserService`) | 200 cancelled / 403 not owner / 409 already cancelled / 400 completed |
| POST | /api/appointments/{id}/reschedule | PATIENT owner only (other patients → 404) | body {newSlotStart:Instant(UTC)} → 200 AppointmentResponse(new slot, status remains `BOOKED`, blocking statuses are `BOOKED`/`RESCHEDULED` so new slot blocks) / 409 occupied/held / 400 past/invalid – original `BOOKED@oldSlot` unchanged on 409 (verified by test) |

Cancellation/Reschedule create Notification(PENDING, CANCELLATION/RESCHEDULE).

## AI Summaries
| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | /api/appointments/{id}/ai/pre-visit | owner patient, assigned doctor, or ADMIN | 200 AiSummaryResponse{type:PRE_VISIT, status, urgency, chiefComplaint, suggestedQuestions[3], modelUsed, createdAt} / 403 not owner / 404 no summary |
| GET | /api/appointments/{id}/ai/post-visit | owner patient, assigned doctor, or ADMIN | 200 AiSummaryResponse{type:POST_VISIT, status, summaryText, medications[], followUpSteps[], modelUsed} / 403 / 404 |
| POST | /api/appointments/{id}/ai/pre-visit/generate | PATIENT owner or ADMIN (manual retry) | 200 AiSummaryResponse (creates PENDING then LLM, validates urgency/chief/3 questions, COMPLETED or FAILED) |
| POST | /api/appointments/{id}/ai/post-visit/generate | DOCTOR owner or ADMIN | 200 AiSummaryResponse (requires Consultation+Prescriptions, validates summary/followUp/meds match DB, COMPLETED or FAILED) |

Pre-visit is also triggered automatically after `POST /{holdId}/confirm` via `AppointmentConfirmedEvent` (`AFTER_COMMIT`), not inside booking transaction. Post-visit triggered after `POST /{id}/consultation` via `ConsultationCompletedEvent`. Failures mark `FAILED` with `failureReason` (no stack), never rollback appointment/consultation.

## Consultation (doctor)
| Method | Path | Auth | Body | Response |
|--------|------|------|------|----------|
| POST | /api/appointments/{id}/consultation | DOCTOR (must own appointment's doctor) | {clinicalNotes, followUpInstructions?, prescriptions?[{medicationName,dosage,frequency,durationDays?,instructions?}]} | 201 ConsultationResponse / 400 already exists / 404 not owner |
| GET | /api/appointments/{id}/consultation | owning PATIENT, assigned DOCTOR, or ADMIN (enforced in service via `CurrentUserService.enforceAppointmentAccess`) | 200 ConsultationResponse / 403 otherwise |

Creates `Consultation` + `PrescriptionItem`s, publishes post-visit AI event `AFTER_COMMIT`.

## Google Calendar (OAuth 2.0)
| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | /api/calendar/connect | isAuthenticated | 200 {authorizationUrl} – Google consent URL embedding a server-generated SINGLE-USE random state (256-bit, bound to the authenticated user in `calendar_oauth_states`, 10-min TTL) |
| GET | /api/calendar/callback | **PUBLIC by design** – browser redirect carries no JWT; caller identified solely by consuming the single-use state | ?code&state → 302 redirect to `{FRONTEND_URL}/calendar/connected?status=success` / 400 invalid-expired-replayed state or code |
| GET | /api/calendar/status | isAuthenticated | 200 {connected:bool, userId} – never exposes tokens |
| POST | /api/calendar/disconnect | isAuthenticated | 200 {status:DISCONNECTED} |

State lifecycle: minted at connect (old unused states for the same user are replaced), deleted on consumption (replay fails with 400), rejected after expiry. userId is never accepted from query parameters.

Appointment event synchronisation is automatic, not manual: confirm/reschedule/cancel enqueue `CalendarSync PENDING` for connected participants; the background worker creates/updates/deletes the Google event (`SYNCED`/`FAILED`/`CANCELLED`). Google failures never affect appointment state. Tokens are never returned by any API.

## Notifications
| Method | Path | Auth | Query | Response |
|--------|------|------|-------|----------|
| GET | /api/notifications | isAuthenticated | ?status=PENDING/SENT/FAILED & type=BOOKING_CONFIRMATION/APPOINTMENT_REMINDER/CANCELLATION/RESCHEDULE/DOCTOR_LEAVE | 200 List<NotificationResponse> (own user, never trusts frontend userId; via `CurrentUserService`) / 401 |
| GET | /api/notifications/{id} | isAuthenticated | | 200 NotificationResponse (owner or ADMIN else 403) / 404 |

`NotificationResponse`: {id, userId, appointmentId?, type, status, subject, body, attemptCount, lastAttemptAt, sentAt, failureReason, createdAt}. No `PATCH /read` – entity has no read flag.

Background: `POST /hold` → `BOOKING_CONFIRMATION PENDING`, `POST /cancel` → `CANCELLATION`, `POST /reschedule` → `RESCHEDULE`, `ReminderScheduler` → `APPOINTMENT_REMINDER` (24h window, idempotent), `DoctorLeaveService` → `DOCTOR_LEAVE` for future affected appointments (past/unaffected ignored). Worker `NotificationProcessor` sends via `EmailService` (`PENDING` → `PROCESSING` → `SENT` / `FAILED` with `failureReason`, `attemptCount++`, backoff 1/5/15 min, `max-retries=3`).

## Common Errors
- 400 – validation, past slot, invalid grid, completed cancel
- 401 – missing/invalid JWT
- 403 – wrong role or not owner
- 404 – unknown doctor/appointment/hold or not owner (masked as 404)
- 409 – slot unavailable, hold expired, already cancelled/converted, concurrent conflict
- 500 – never for business errors (DataIntegrityViolation mapped to 409)

## Pagination
Not required; lists are returned full.

## Notes
- All times are UTC Instants (`2026-08-25T10:00:00Z`). Do not send local time zones.
- Never trust `patientId` from body – always `CurrentUserService`.
- Slot validation via `AvailabilityService.isValidSlot` – reuses working-hours grid.
- No raw LLM responses, prompts, or API keys exposed via API.
