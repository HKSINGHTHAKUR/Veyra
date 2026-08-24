# Healthcare Appointment & Follow-up Manager

A full-stack application for managing healthcare appointments, doctor schedules,
patient records, AI-assisted pre/post-visit summaries, notifications, and Google
Calendar synchronisation.

> **Status:** Active development — see phase progress below.

## Tech Stack

| Layer    | Technology                                      |
|----------|-------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.2, Spring Security, JPA  |
| Database | PostgreSQL                                      |
| Auth     | JWT (RS-256 / HS-256)                           |
| AI       | OpenAI API (gpt-4o-mini)                        |
| Calendar | Google Calendar API (OAuth 2.0)                 |
| Email    | Spring Mail (SMTP)                              |
| Frontend | React 18, Vite, React Router, Axios, Tailwind   |

## Project Structure

```
healthcare-appointment-manager/
├── backend/          # Spring Boot Maven project
│   └── src/
│       ├── main/java/com/healthcare/appointment/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── entity/
│       │   ├── exception/
│       │   ├── repository/
│       │   ├── security/
│       │   ├── service/
│       │   ├── integration/
│       │   │   ├── ai/
│       │   │   ├── email/
│       │   │   └── calendar/
│       │   └── scheduler/
│       └── main/resources/
└── frontend/         # React + Vite project
    └── src/
        ├── components/
        ├── context/
        ├── hooks/
        ├── layouts/
        ├── pages/
        │   ├── admin/
        │   ├── auth/
        │   ├── doctor/
        │   └── patient/
        ├── routes/
        ├── services/
        └── utils/
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 20+
- PostgreSQL 15+

## Local Setup

### 1. Database

```sql
CREATE DATABASE healthcare_appointments;
```

### 2. Backend

```bash
cd backend
cp .env.example .env        # fill in your values
mvn spring-boot:run
```

The API runs on **http://localhost:8080**.

### 3. Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

The UI runs on **http://localhost:5173**.

## Environment Variables

See `backend/.env.example` and `frontend/.env.example` for the full list.
Never commit `.env` files — they are in `.gitignore`.

## Build

```bash
# Backend
cd backend && mvn clean package

# Frontend
cd frontend && npm run build
```

## Phase Progress

| Phase | Description                          | Status      |
|-------|--------------------------------------|-------------|
| 0     | Repository setup                     | ✅ Complete |
| 1     | Database + entities (Flyway V1, UTC) | ✅ Complete |
| 2     | Authentication + RBAC (JWT, BCrypt)  | ✅ Complete |
| 3     | Doctor management + availability     | ✅ Complete |
| 4     | Appointment booking + concurrency    | ✅ Complete |
| 5     | Symptoms + pre-visit AI              | ✅ Complete (pre-visit urgency/chief/3 questions, failure isolation, no LLM creds needed) |
| 6     | Consultation + prescription + AI     | ✅ Complete (consultation, prescription, post-visit summary/meds/follow-up, DB authoritative) |
| 7     | Notifications + background jobs      | ✅ Complete (confirmation/cancellation/reschedule/reminder/leave, PENDING→PROCESSING→SENT/FAILED, retry 3, backoff, worker, no SMTP in tx) |
| 8     | Google Calendar integration          | ✅ Complete (OAuth 2.0 connect/callback/status/disconnect, background event create/update/delete, bounded retries, failure isolation, fake client tests) |
| 9     | Frontend                             | ⬜ Pending  |
| 10    | Testing + integration + polish       | ⬜ Pending  |
| 11    | Documentation                        | ✅ Complete (docs/ updated for notifications) |
| 12    | Deployment preparation               | ⬜ Pending  |

## Current Features (Notification Phase)

- JWT auth (PATIENT/DOCTOR/ADMIN), registration, login, RBAC
- Admin doctor CRUD, working hours, leaves
- Patient doctor search (specialisation, active only), profile view
- Dynamic availability (workingHours - leave - BOOKED/RESCHEDULED - ACTIVE holds - past) with grid validation
- Booking engine: `POST /api/appointments/hold` → `POST /{holdId}/symptoms` → `POST /{holdId}/confirm` → BOOKED, hold CONVERTED
- Appointment ownership (patient own, doctor own, admin), cancellation, rescheduling (safe, original preserved on 409)
- Hold expiry scheduler (60s) and inline expiry handling, notification records (PENDING)
- Double-booking protection via partial unique indexes + 409 mapping, concurrency tests
- AI pre-visit: urgency (LOW/MEDIUM/HIGH), chief complaint, exactly 3 questions (validated, `PRE_VISIT_V1`), `AFTER_COMMIT` event, no booking rollback on LLM failure
- AI post-visit: patient-friendly summary, medication schedule (DB authoritative), follow-up steps (validated, `POST_VISIT_V1`), consultation triggers, no invented meds
- AI failure handling: `PENDING→COMPLETED/FAILED`, `FAILED` with `failureReason`, timeout, duplicate prevention via `uq_ai_appointment_type`, no secrets hardcoded
- Notifications: `BOOKING_CONFIRMATION`/`CANCELLATION`/`RESCHEDULE`/`APPOINTMENT_REMINDER` (24h window, idempotent)/`DOCTOR_LEAVE` (future affected only, past/unaffected ignored), `PENDING→PROCESSING→SENT/FAILED`, `EmailService` abstraction (`Smtp` vs `Fake`), `EmailTemplateService`, `NotificationProcessor` (claim `PENDING`/`FAILED`→`PROCESSING`, `max-retries 3`, backoff 1/5/15 min, `worker-batch 20`, `attemptCount`/`failureReason`/`sentAt`), `ReminderScheduler` (24h ±30m), no email in booking tx, ownership via `CurrentUserService`
- Google Calendar: OAuth 2.0 (`/api/calendar/connect|callback|status|disconnect`, state-validated, tokens never exposed/logged), background sync worker creates/updates/deletes events from DB state (idempotent, bounded retries, backoff), failure isolation – appointment never depends on Google availability, event content contains scheduling facts only (no clinical data), UTC RFC-3339

## Documentation

| Doc | Content |
|-----|---------|
| [architecture.md](docs/architecture.md) | Layers, modules, AI, structure |
| [database.md](docs/database.md) | Tables, V2/V3 migrations, indexes, time zone |
| [api.md](docs/api.md) | All endpoints (incl. AI/consultation), auth, errors |
| [booking-concurrency.md](docs/booking-concurrency.md) | Holds, unique indexes, transactions, H2 limitation |
| [system-design.md](docs/system-design.md) | <800w design summary (incl. AI isolation) |
| [notifications.md](docs/notifications.md) | Lifecycle, email abstraction, scheduler, retries, failure logging, duplicate prevention, leave |
| [google-calendar.md](docs/google-calendar.md) | OAuth flow, token handling, sync lifecycle, retries, failure isolation, testing |
| [llm.md](docs/llm.md) | AI architecture, prompts, failure handling, testing |

## Testing

```bash
cd backend
mvn clean test   # 122 tests: auth 10, availability 9, doctor 11, additional 10, booking 29, concurrency 2, ai 17, notifications 19, calendar 14, pg-concurrency (skipped), app 1
mvn compile
```
- H2 in tests (`create-drop`); PostgreSQL in prod (Flyway V1-V5, partial indexes). Concurrency tests document H2 limitation (see booking-concurrency.md); `PostgresBookingConcurrencyIT` requires `pgtest` profile.
- Slot hold duration: 5 min test, 10 min prod via `SLOT_HOLD_DURATION_MINUTES`.
- Notifications: 19 tests use `FakeEmailService` (no SMTP); Calendar: 14 tests use `FakeGoogleCalendarClient` (no Google credentials/network); AI uses `FakeLlmClient`.
- Worker/reminder/sync schedulers are invoked directly for determinism.

## Upcoming

- Frontend: patient/doctor/admin portals

## License

For academic use only.
// & ".\.tools\pg\pgsql\bin\postgres.exe" -D ".\.tools\pgdata" - postger sql

// & ".\.tools\start-backend.ps1" - backend(ye root folder mein chalana , backend mein nhi)

// npm run dev - frontend


//plsql entry - & ".\.tools\pg\pgsql\bin\psql.exe" -U postgres -d healthcare_appointments

\dt

5. Demo ke liye sabse useful view

SELECT
    pu.email AS patient,
    du.email AS doctor,
    a.appointment_start,
    a.status
FROM appointments a
JOIN patients p ON p.id = a.patient_id
JOIN users pu ON pu.id = p.user_id
JOIN doctors d ON d.id = a.doctor_id
JOIN users du ON du.id = d.user_id
ORDER BY a.appointment_start DESC;
press enter;

6. Aur agar dekhna hai demo doctor ke slots/working hours

SELECT
    u.email,
    dwh.day_of_week,
    dwh.start_time,
    dwh.end_time,
    dwh.slot_duration_minutes,
    dwh.active
FROM doctor_working_hours dwh
JOIN doctors d ON d.id = dwh.doctor_id
JOIN users u ON u.id = d.user_id
ORDER BY dwh.day_of_week;



