# Booking & Concurrency

## Race Condition
Two patients simultaneously request `Doctor X / 10:00`. Naïve `if(available) create()` allows both to pass check before either writes – double booking.

## Hold Lifecycle
```
AVAILABLE → HELD(ACTIVE, expiresAt=now+5/10m) → BOOKED(CONVERTED)
                     ↓ EXPIRED (scheduler or inline) / RELEASED
BOOKED → CANCELLED (frees slot)
BOOKED --reschedule--> BOOKED@newSlot (atomic, original preserved on 409)
```

## Slot Hold Protection
**App guard + DB guard.**

1. **Pre-check** in `SlotHoldService.createHold`:
   - verify doctor active, slot on grid (`AvailabilityService.isValidSlot`), not on leave, not blocked by `BOOKED/RESCHEDULED`, not held by non-expired ACTIVE hold (if expired ACTIVE found, mark EXPIRED inline).
2. **Insert** `slot_holds` with `status=ACTIVE`. 
3. **DB final guard** – partial unique index:
   ```sql
   CREATE UNIQUE INDEX uq_slot_hold_active ON slot_holds (doctor_id, slot_start) WHERE status='ACTIVE';
   ```
   Concurrent inserts race to commit; second gets `DataIntegrityViolationException` → mapped to `409 SLOT_UNAVAILABLE`. No fake `SELECT FOR UPDATE` on nonexistent row.

**Expired holds:** considered `status=ACTIVE AND expiresAt > now` only. Availability filters with `expiresAt > now`; hold creation expires stale row before insert; correctness does not depend on scheduler.

## Appointment Confirmation Protection
1. Load hold with `SELECT ... FOR UPDATE` (`findByIdForUpdate`) – hold exists, so lock is effective.
2. Revalidate: ownership, ACTIVE, not expired, doctor active/not on leave, valid grid, not booked.
3. **Insert** `appointments` with `status=BOOKED`.
4. **DB final guard**:
   ```sql
   CREATE UNIQUE INDEX uq_appointment_active ON appointments (doctor_id, appointment_start) WHERE status IN ('BOOKED','RESCHEDULED');
   ```
   Concurrent confirmations race; second insert violates index → caught → `409`. Transaction rolls back, hold stays ACTIVE (not converted), no orphan.

5. On success: link `symptom_submissions` (slot_hold_id → appointment_id), hold → CONVERTED, create `notifications` PENDING.

No email/calendar/LLM inside booking transaction – only notification *record* creation (still within transaction but not external call).

## Rescheduling Safety
Rescheduling does **not** cancel first. It validates new slot exactly like a new booking (grid, leave, not booked, not held), then attempts `UPDATE appointments SET appointment_start=new, appointment_end=new+duration`. DB unique index protects new slot concurrently. On `DataIntegrityViolation` the `UPDATE` rolls back, original appointment remains `BOOKED@oldSlot`, new slot stays as before. Notification only created on success.

## Cancellation
Sets `status=CANCELLED, cancelled_at=now`. Dynamic availability excludes `CANCELLED`, so slot becomes free immediately. No slot_hold involved.

## Scheduler
`HoldExpiryScheduler` (`@Scheduled fixedDelay 60s`) bulk-expires `ACTIVE && expiresAt < now → EXPIRED`. Idempotent, safe to run twice. It is **cleanup only** – correctness already handled by `expiresAt > now` checks.

## H2 vs PostgreSQL
Production PostgreSQL enforces partial indexes (`uq_slot_hold_active WHERE ACTIVE`, `uq_appointment_active WHERE BOOKED/RESCHEDULED`). H2 `create-drop` (default `test` profile) does **not** create partial indexes, so concurrent inserts may both succeed on H2.
- Default tests (`test` profile, H2) assert `success >=1` and log `"H2 limitation: ... PostgreSQL would enforce 1"` – they prove competing requests are executed, not the final DB guarantee.
- **PostgreSQL integration verification**: `src/test/resources/application-pgtest.yml` (profile `pgtest`, Flyway `validate`, real PostgreSQL schema) + `PostgresBookingConcurrencyIT` (requires running PostgreSQL on `localhost:5432/healthcare_appointments_test`). When `pgtest` is active, the test asserts strict `success==1, conflict==1` for both holds and appointments and verifies `exactly one BOOKED` in DB. In the current environment Docker/PostgreSQL is unavailable, so the `pgtest` is skipped via `assumeTrue`; the final uniqueness guarantee remains PostgreSQL, not H2. To run: `docker run -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:15` then `mvn test -Dspring.profiles.active=pgtest -Dtest=PostgresBookingConcurrencyIT`.

## Transaction Boundaries
- `createHold`: `@Transactional` – pre-checks + insert + catch `DataIntegrityViolationException`.
- `confirmHold`: `@Transactional` – lock hold, revalidate, insert appointment + catch `DataIntegrityViolationException`, convert hold, notifications. All or nothing.
- `reschedule`: `@Transactional` – validate new slot, update appointment + catch, notifications.
- `cancel`: `@Transactional`.

## Conflict Handling
`GlobalExceptionHandler` maps `ConflictException` and `DataIntegrityViolationException` to `409` with `ErrorResponse{status:409, message}` – never exposes `uq_appointment_active` or SQL to client.
