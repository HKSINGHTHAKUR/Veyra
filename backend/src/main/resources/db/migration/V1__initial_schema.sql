-- =============================================================================
-- V1__initial_schema.sql
-- Initial schema for the Healthcare Appointment & Follow-up Manager.
-- =============================================================================

-- pgcrypto supplies gen_random_uuid(); used as the default for UUID columns.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- USERS
-- Central authentication identity shared by PATIENT, DOCTOR, and ADMIN roles.
-- =============================================================================
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role  ON users (role);

-- =============================================================================
-- PATIENTS
-- Profile data linked to a user account with PATIENT role.
-- =============================================================================
CREATE TABLE patients (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    date_of_birth DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_patients_user UNIQUE (user_id)
);

-- =============================================================================
-- DOCTORS
-- Profile data linked to a user account with DOCTOR role.
-- =============================================================================
CREATE TABLE doctors (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID          NOT NULL,
    specialisation   VARCHAR(150)  NOT NULL,
    bio              TEXT,
    consultation_fee NUMERIC(10,2),
    active           BOOLEAN       NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_doctors_user UNIQUE (user_id)
);

CREATE INDEX idx_doctors_specialisation ON doctors (specialisation);
CREATE INDEX idx_doctors_active         ON doctors (active);

-- =============================================================================
-- DOCTOR WORKING HOURS
-- Recurring weekly schedule.  One row per doctor per day of week.
-- Availability for a given date is derived from this table minus leaves minus
-- existing booked appointments minus active slot holds.
-- =============================================================================
CREATE TABLE doctor_working_hours (
    id                    UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id             UUID    NOT NULL,
    day_of_week           VARCHAR(10) NOT NULL,
    start_time            TIME    NOT NULL,
    end_time              TIME    NOT NULL,
    slot_duration_minutes INT     NOT NULL DEFAULT 30,
    active                BOOLEAN NOT NULL DEFAULT true,

    CONSTRAINT fk_dwh_doctor    FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT uq_dwh_doctor_day UNIQUE (doctor_id, day_of_week),
    CONSTRAINT chk_dwh_day CHECK (
        day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')
    ),
    CONSTRAINT chk_dwh_times         CHECK (end_time > start_time),
    CONSTRAINT chk_dwh_slot_duration CHECK (slot_duration_minutes > 0 AND slot_duration_minutes <= 480)
);

CREATE INDEX idx_dwh_doctor ON doctor_working_hours (doctor_id);

-- =============================================================================
-- DOCTOR LEAVES
-- Dates on which a doctor is unavailable.
-- Prevents duplicate leave records for the same doctor on the same date.
-- =============================================================================
CREATE TABLE doctor_leaves (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id  UUID         NOT NULL,
    leave_date DATE         NOT NULL,
    reason     VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_leaves_doctor   FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT uq_leave_doctor_date UNIQUE (doctor_id, leave_date)
);

CREATE INDEX idx_leaves_doctor ON doctor_leaves (doctor_id);
CREATE INDEX idx_leaves_date   ON doctor_leaves (leave_date);

-- =============================================================================
-- SLOT HOLDS
-- A patient can temporarily reserve a slot before confirming an appointment.
-- The partial unique index ensures only one ACTIVE hold per doctor+time exists,
-- preventing two patients from holding the same slot simultaneously.
-- =============================================================================
CREATE TABLE slot_holds (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id  UUID        NOT NULL,
    patient_id UUID        NOT NULL,
    slot_start TIMESTAMPTZ NOT NULL,
    slot_end   TIMESTAMPTZ NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    held_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_holds_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors  (id),
    CONSTRAINT fk_holds_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT chk_holds_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CONVERTED', 'RELEASED')),
    CONSTRAINT chk_holds_times  CHECK (slot_end > slot_start)
);

-- Concurrency constraint: only one ACTIVE hold per doctor + slot start time.
CREATE UNIQUE INDEX uq_slot_hold_active
    ON slot_holds (doctor_id, slot_start)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_holds_patient    ON slot_holds (patient_id);
CREATE INDEX idx_holds_status     ON slot_holds (status);
CREATE INDEX idx_holds_expires_at ON slot_holds (expires_at);

-- =============================================================================
-- APPOINTMENTS
-- A confirmed booking linking a patient, doctor, and time window.
--
-- DOUBLE-BOOKING PREVENTION (Phase 4 implementation target):
-- The partial unique index below makes it impossible at the database level
-- for two BOOKED/RESCHEDULED appointments to share the same doctor + start time.
-- Combined with SELECT FOR UPDATE on SlotHold rows in Phase 4, this provides
-- safe concurrent booking without application-level race conditions.
-- =============================================================================
CREATE TABLE appointments (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID        NOT NULL,
    doctor_id           UUID        NOT NULL,
    slot_hold_id        UUID,
    appointment_start   TIMESTAMPTZ NOT NULL,
    appointment_end     TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    booked_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    rescheduled_from_id UUID,

    CONSTRAINT fk_appt_patient          FOREIGN KEY (patient_id)          REFERENCES patients      (id),
    CONSTRAINT fk_appt_doctor           FOREIGN KEY (doctor_id)           REFERENCES doctors       (id),
    CONSTRAINT fk_appt_hold             FOREIGN KEY (slot_hold_id)        REFERENCES slot_holds    (id),
    CONSTRAINT fk_appt_rescheduled_from FOREIGN KEY (rescheduled_from_id) REFERENCES appointments  (id),
    CONSTRAINT chk_appt_status CHECK (
        status IN ('BOOKED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED', 'NO_SHOW')
    ),
    CONSTRAINT chk_appt_times CHECK (appointment_end > appointment_start)
);

-- KEY CONSTRAINT: prevents double-booking at the database level.
CREATE UNIQUE INDEX uq_appointment_active
    ON appointments (doctor_id, appointment_start)
    WHERE status IN ('BOOKED', 'RESCHEDULED');

CREATE INDEX idx_appt_patient      ON appointments (patient_id);
CREATE INDEX idx_appt_doctor       ON appointments (doctor_id);
CREATE INDEX idx_appt_status       ON appointments (status);
CREATE INDEX idx_appt_doctor_start ON appointments (doctor_id, appointment_start);
CREATE INDEX idx_appt_start        ON appointments (appointment_start);

-- =============================================================================
-- SYMPTOM SUBMISSIONS
-- Patient-reported symptoms submitted before an appointment.
-- One appointment can have one symptom submission.
-- =============================================================================
CREATE TABLE symptom_submissions (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID        NOT NULL,
    patient_id     UUID        NOT NULL,
    symptom_text   TEXT        NOT NULL,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_symptoms_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_symptoms_patient     FOREIGN KEY (patient_id)     REFERENCES patients     (id),
    CONSTRAINT uq_symptoms_appointment UNIQUE (appointment_id)
);

CREATE INDEX idx_symptoms_appointment ON symptom_submissions (appointment_id);

-- =============================================================================
-- AI SUMMARIES
-- Stores AI-generated pre-visit and post-visit summaries.
-- PRE_VISIT  fields: urgency, chief_complaint, suggested_questions (JSON text)
-- POST_VISIT fields: summary_text
-- One summary per appointment per type.
-- =============================================================================
CREATE TABLE ai_summaries (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID        NOT NULL,
    type                VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    urgency             VARCHAR(10),
    chief_complaint     TEXT,
    suggested_questions TEXT,
    summary_text        TEXT,
    model_used          VARCHAR(100),
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_appointment   FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT uq_ai_appointment_type UNIQUE (appointment_id, type),
    CONSTRAINT chk_ai_type    CHECK (type    IN ('PRE_VISIT', 'POST_VISIT')),
    CONSTRAINT chk_ai_status  CHECK (status  IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_ai_urgency CHECK (urgency IS NULL OR urgency IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX idx_ai_appointment ON ai_summaries (appointment_id);
CREATE INDEX idx_ai_status      ON ai_summaries (status);

-- =============================================================================
-- CONSULTATIONS
-- Clinical notes recorded by the doctor after the appointment.
-- One consultation per appointment.
-- =============================================================================
CREATE TABLE consultations (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id          UUID        NOT NULL,
    doctor_id               UUID        NOT NULL,
    clinical_notes          TEXT        NOT NULL,
    follow_up_instructions  TEXT,
    consultation_timestamp  TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_consult_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_consult_doctor      FOREIGN KEY (doctor_id)      REFERENCES doctors      (id),
    CONSTRAINT uq_consult_appointment UNIQUE (appointment_id)
);

CREATE INDEX idx_consult_appointment ON consultations (appointment_id);
CREATE INDEX idx_consult_doctor      ON consultations (doctor_id);

-- =============================================================================
-- PRESCRIPTION ITEMS
-- Medications prescribed within a consultation.
-- A consultation can have multiple prescription items.
-- =============================================================================
CREATE TABLE prescription_items (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID         NOT NULL,
    medication_name VARCHAR(255) NOT NULL,
    dosage          VARCHAR(100) NOT NULL,
    frequency       VARCHAR(100) NOT NULL,
    duration_days   INT,
    instructions    TEXT,

    CONSTRAINT fk_rx_consultation FOREIGN KEY (consultation_id) REFERENCES consultations (id)
);

CREATE INDEX idx_rx_consultation ON prescription_items (consultation_id);

-- =============================================================================
-- NOTIFICATIONS
-- Tracks every outbound notification (email, reminder, etc.) with retry state.
-- =============================================================================
CREATE TABLE notifications (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    appointment_id  UUID,
    type            VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    subject         VARCHAR(500),
    body            TEXT,
    attempt_count   INT         NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_notif_user        FOREIGN KEY (user_id)        REFERENCES users        (id),
    CONSTRAINT fk_notif_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT chk_notif_type CHECK (type IN (
        'BOOKING_CONFIRMATION', 'APPOINTMENT_REMINDER', 'CANCELLATION',
        'RESCHEDULE', 'DOCTOR_LEAVE', 'MEDICATION_REMINDER'
    )),
    CONSTRAINT chk_notif_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notif_user        ON notifications (user_id);
CREATE INDEX idx_notif_status      ON notifications (status);
CREATE INDEX idx_notif_type        ON notifications (type);
CREATE INDEX idx_notif_appointment ON notifications (appointment_id);

-- =============================================================================
-- CALENDAR SYNCS
-- Tracks the state of Google Calendar event synchronisation per appointment.
-- OAuth credential storage is handled separately in Phase 8.
-- =============================================================================
CREATE TABLE calendar_syncs (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id       UUID        NOT NULL,
    user_id              UUID        NOT NULL,
    google_event_id      VARCHAR(255),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_sync_attempt_at TIMESTAMPTZ,
    error_message        TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_cal_appointment    FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_cal_user           FOREIGN KEY (user_id)        REFERENCES users        (id),
    CONSTRAINT uq_cal_appointment_user UNIQUE (appointment_id, user_id),
    CONSTRAINT chk_cal_status CHECK (status IN ('PENDING', 'SYNCED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_cal_appointment ON calendar_syncs (appointment_id);
CREATE INDEX idx_cal_status      ON calendar_syncs (status);
