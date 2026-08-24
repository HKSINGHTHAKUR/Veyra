# Architecture

## Overview
Modular monolith – Spring Boot (Java 21) + React. PostgreSQL is source of truth. All business rules live in service layer; controllers are thin and handle auth/validation only.

## Layers
```
React (Vite, Router, Axios, Tailwind)
   ↓ HTTP / JSON
Controller (Spring MVC, @PreAuthorize, @Valid, DTOs)
   ↓
Service (@Transactional, CurrentUserService for ownership, AvailabilityService for slot grid)
   ↓
Repository (Spring Data JPA, JPQL, @Lock, partial-index relies on DB)
   ↓
PostgreSQL (Flyway migrations, ddl-auto validate)
```

External integrations are separate modules (`integration/ai`, `integration/email`, `integration/calendar`) – not called inside booking transactions; AI and email are triggered after commit via events/schedulers.

## Key Modules
- **Auth** – JWT (jjwt 0.12.5), BCrypt(10), stateless, UserDetailsServiceImpl, JwtAuthFilter.
- **Doctor Management** – DoctorService, WorkingHoursService, DoctorLeaveService.
- **Availability** – AvailabilityService (dynamic, no slot rows).
- **Booking** – SlotHoldService, SymptomService, AppointmentService, HoldExpiryScheduler.
- **AI** – AiSummaryService → LlmClient (OpenAiLlmClient / FakeLlmClient), AiPromptTemplates (`PRE_VISIT_V1`/`POST_VISIT_V1`), `AiSummaryEventListener` (`@TransactionalEventListener AFTER_COMMIT`), resource prompts `prompts/pre_visit_v1.txt`.
- **Consultation** – ConsultationService + PrescriptionItem, triggers post-visit AI.
- **Notifications** – NotificationService (PENDING creation) → `NotificationProcessor` (`@Scheduled 30s`, claim `PENDING/FAILED→PROCESSING`, `EmailService` abstraction `SmtpEmailService`/`FakeEmailService`, `EmailTemplateService`, backoff 1/5/15 min, `max-retries 3`) + `ReminderScheduler` (`@Scheduled 60s`, 24h window) + `DoctorLeaveService` leave notifications.
- **Calendar** – `CalendarService` (OAuth connect/callback/status/disconnect, token refresh, min token storage in `calendar_credentials`) → `GoogleCalendarClient` (`HttpGoogleCalendarClient` / `FakeGoogleCalendarClient`); `CalendarSyncService` enqueues per-appointment syncs (DB-only inside booking tx) and processes them via `CalendarSyncWorker` (`@Scheduled 60s`) with bounded retries; idempotent create/update/delete derived from DB state.
- **Scheduler** – @EnableScheduling + @EnableAsync: hold expiry 60s, notifications 30s, reminders 60s, calendar sync 60s.
- **Security** – SecurityConfig (CORS, stateless, 401/403 handlers), CurrentUserService.

## Backend Structure
```
com.healthcare.appointment
 ├── config (SecurityConfig, DataSeeder)
 ├── controller (Auth, Admin, Doctors, Appointment, Patient, Doctor, AiSummary, Consultation)
 ├── dto (requests/responses, never entities)
 ├── entity (JPA, Auditable)
 ├── repository (JpaRepository, @Query, @Modifying, @Lock)
 ├── service (business logic, AiSummaryService, ConsultationService, NotificationService)
 ├── integration/ai (LlmClient, OpenAiLlmClient, FakeLlmClient, AiPromptTemplates)
 ├── integration/email (EmailService, SmtpEmailService, FakeEmailService, EmailTemplateService)
 ├── integration/calendar (GoogleCalendarClient, HttpGoogleCalendarClient, FakeGoogleCalendarClient)
 ├── event (AppointmentConfirmedEvent, ConsultationCompletedEvent, AiSummaryEventListener)
 ├── scheduler (HoldExpiryScheduler, NotificationProcessor, ReminderScheduler)
 ├── security (JwtService, UserPrincipal, JwtAuthFilter)
 └── exception (GlobalExceptionHandler, ErrorResponse)
```

## Frontend Structure
```
frontend/src
 ├── components, context, hooks, layouts
 ├── pages/{admin,auth,doctor,patient}
 ├── routes, services (axios), utils
```

## Configuration
- `application.yml` – datasource, flyway, JPA (validate, UTC time_zone), mail, jwt, ai, google, slot, frontend.
- `application-test.yml` – H2 mem, create-drop, test jwt secret.
- `.env.example` documents all env vars; `.env` is gitignored.
