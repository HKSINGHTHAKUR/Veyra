-- Cleanup script for 2026-09-09 demo appointment
-- Run in order due to foreign key constraints:
-- 1. ai_summaries (references appointment_id)
-- 2. notifications (references appointment_id)
-- 3. symptom_submissions (references appointment_id)
-- 4. appointments (parent table)
-- 5. slot_holds (only ACTIVE holds on 2026-09-09)

-- Get appointment IDs for 2026-09-09
WITH target_appointments AS (
    SELECT id FROM appointments WHERE appointment_start::date = '2026-09-09'
)

-- Delete from ai_summaries first (FK: ai_appointment)
DELETE FROM ai_summaries WHERE appointment_id IN (SELECT id FROM target_appointments);

-- Delete from notifications (FK: notif_appointment)
DELETE FROM notifications WHERE appointment_id IN (SELECT id FROM target_appointments);

-- Delete from symptom_submissions (FK: fk_symptoms_appointment)
DELETE FROM symptom_submissions WHERE appointment_id IN (SELECT id FROM target_appointments);

-- Delete from appointments
DELETE FROM appointments WHERE appointment_start::date = '2026-09-09';

-- Delete from slot_holds (only ACTIVE holds on 2026-09-09, and those referencing today's appointments)
DELETE FROM slot_holds 
WHERE slot_start::date = '2026-09-09' 
  AND status = 'ACTIVE'
  AND id NOT IN (SELECT slot_hold_id FROM appointments WHERE slot_hold_id IS NOT NULL);

-- Return final counts
SELECT 
    'Final appointments count: ' || (SELECT count(*) FROM appointments WHERE appointment_start::date = '2026-09-09') AS appointments_count,
    'Final holds count: ' || (SELECT count(*) FROM slot_holds WHERE slot_start::date = '2026-09-09' AND status = 'ACTIVE') AS holds_count;