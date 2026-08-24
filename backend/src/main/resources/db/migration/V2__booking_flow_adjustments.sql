-- =============================================================================
-- V2__booking_flow_adjustments.sql
-- Phase 4: Allow symptoms to be submitted before an appointment exists.
-- The original V1 schema tied SymptomSubmission strictly to Appointment
-- (appointment_id NOT NULL, unique). Phase 4 booking lifecycle requires
-- symptoms at HOLD stage (before appointment is confirmed).
--
-- Solution: make appointment_id nullable and add slot_hold_id so a symptom
-- can be attached to a hold and later linked to the confirmed appointment.
-- No second symptom table is created.
-- =============================================================================

-- Make appointment_id nullable ( originally NOT NULL )
ALTER TABLE symptom_submissions ALTER COLUMN appointment_id DROP NOT NULL;

-- Add slot_hold reference (nullable, holds are transient)
ALTER TABLE symptom_submissions ADD COLUMN slot_hold_id UUID;

ALTER TABLE symptom_submissions
    ADD CONSTRAINT fk_symptoms_hold
    FOREIGN KEY (slot_hold_id) REFERENCES slot_holds (id);

-- Ensure at most one symptom per hold (when hold-based)
CREATE UNIQUE INDEX uq_symptoms_hold
    ON symptom_submissions (slot_hold_id)
    WHERE slot_hold_id IS NOT NULL;

CREATE INDEX idx_symptoms_hold ON symptom_submissions (slot_hold_id);

-- Existing unique on appointment_id already allows multiple NULLs in PostgreSQL,
-- so no change needed there. The unique index uq_symptoms_appointment
-- continues to enforce one symptom per appointment when appointment_id is set.
