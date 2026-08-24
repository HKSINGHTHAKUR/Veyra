-- V3: Post-visit AI fields (medications, follow-up steps) stored as JSON arrays.
ALTER TABLE ai_summaries ADD COLUMN medications TEXT;
ALTER TABLE ai_summaries ADD COLUMN follow_up_steps TEXT;
