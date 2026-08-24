-- =============================================================================
-- V5__calendar_sync_adjustments.sql
-- Google Calendar integration (OAuth 2.0 + background synchronisation).
--
-- 1. calendar_syncs gains attempt_count for bounded retries.
-- 2. New calendar_credentials table stores the MINIMUM OAuth state per user:
--    refresh token, current access token, expiry. This is credential storage,
--    NOT a duplicate of calendar_syncs (which tracks per-appointment syncs).
--    CalendarSync intentionally holds no tokens (see V1 comment).
-- =============================================================================

ALTER TABLE calendar_syncs ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

CREATE TABLE calendar_credentials (
    id                       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID        NOT NULL,
    refresh_token            TEXT,
    access_token             TEXT,
    access_token_expires_at  TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_cred_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_credentials_user UNIQUE (user_id)
);

CREATE INDEX idx_credentials_user ON calendar_credentials (user_id);
