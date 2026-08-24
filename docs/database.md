# Database

## Engine
PostgreSQL (prod) via Flyway migrations V1, V2, V3, V5, V6. H2 in tests (`create-drop`). JPA `ddl-auto: validate` in prod.
Note: **V4 was never created** — the numbering jumps from V3 to V5 (harmless to Flyway; retained for audit-trail honesty).

## Tables
| Table | PK | Key FK/Constraints |
|-------|----|---------------------|
| users | id UUID | uq_users_email, chk_role |
| patients | id UUID | uq_patients_user (user_id), FK users |
| doctors | id UUID | uq_doctors_user, idx_specialisation, active |
| doctor_working_hours | id UUID | uq_dwh_doctor_day (doctor_id, day_of_week), chk_times, chk_slot_duration |
| doctor_leaves | id UUID | uq_leave_doctor_date (doctor_id, leave_date) |
| slot_holds | id UUID | fk doctor/patient, chk_status/holds_times, uq_slot_hold_active (partial), idx_expires |
| appointments | id UUID | fk patient/doctor/hold, chk_status/times, uq_appointment_active (partial), indexes |
| symptom_submissions | id UUID | fk appointment (nullable after V2), fk slot_hold, fk patient, uq per appointment/hold |
| ai_summaries | id UUID | uq per appointment+type, chk type/status |
| consultations | id UUID | uq per appointment |
| prescription_items | id UUID | fk consultation |
| notifications | id UUID | fk user/appointment, chk type/status |
| calendar_syncs | id UUID | uq per appointment+user, chk status |

## Important Relationships
- `doctors.user_id` 1-1 `users` (role DOCTOR)
- `patients.user_id` 1-1 `users` (role PATIENT)
- `doctor_working_hours.doctor_id` M-1 doctors
- `slot_holds` M-1 doctors/patients
- `appointments` M-1 doctors/patients, optional slot_hold_id
- `symptom_submissions` after V2: optional appointment_id OR slot_hold_id, mandatory patient_id
- `notifications.user_id` M-1 users, optional appointment_id (BOOKING_CONFIRMATION, CANCELLATION, RESCHEDULE)

## Indexes & Constraints
```sql
-- Only one ACTIVE hold per doctor+slot
CREATE UNIQUE INDEX uq_slot_hold_active ON slot_holds (doctor_id, slot_start) WHERE status='ACTIVE';
-- Only one BOOKED/RESCHEDULED appointment per doctor+slot
CREATE UNIQUE INDEX uq_appointment_active ON appointments (doctor_id, appointment_start) WHERE status IN ('BOOKED','RESCHEDULED');
-- One working-hours row per doctor per day
UNIQUE (doctor_id, day_of_week)
-- One leave per doctor per date
UNIQUE (doctor_id, leave_date)
```

## V2 Booking Flow Adjustment
`V2__booking_flow_adjustments.sql` – makes `symptom_submissions.appointment_id` nullable, adds `slot_hold_id` (FK slot_holds, unique partial index) so symptoms can be submitted at HOLD stage before appointment exists. Production uses Flyway; H2 tests use JPA entity change (nullable appointment, optional slotHold).

## Dynamic Slot Design
No `slots` table. Availability is derived per request:
`working_hours → candidate grid (start+ N*duration) → leave → BOOKED/RESCHEDULED appointments → ACTIVE non-expired holds → past filter`. Time stored as `TIMESTAMPTZ` (UTC via `hibernate.jdbc.time_zone=UTC`), derived from `LocalDate+LocalTime → Instant (ZoneOffset.UTC)`.

## Time Zone
All slot Instants are UTC. `application.yml` sets `hibernate.jdbc.time_zone: UTC`. Services consistently use `ZoneOffset.UTC`.

## Migrations
- V1 – initial schema (all tables, indexes, checks)
- V2 – symptom pre-appointment support (nullable appointment_id + slot_hold_id)
- V3 – AI post-visit fields (`ai_summaries.medications`, `follow_up_steps`)
- V4 – **never created** (numbering gap; V5 was written next)
- V5 – calendar sync adjustments: `calendar_syncs.attempt_count` (bounded retries) + new `calendar_credentials` table (minimum per-user Google OAuth token storage: refresh/access tokens + expiry, unique per user)
- V6 – `calendar_oauth_states`: single-use, short-lived OAuth state rows (256-bit random state UNIQUE, user FK, expires_at) enabling the JWT-free browser callback

Do not edit existing migrations; create new ones for future changes.
