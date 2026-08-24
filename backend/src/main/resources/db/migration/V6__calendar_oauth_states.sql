-- =============================================================================
-- V6__calendar_oauth_states.sql
-- Secure browser-compatible Google OAuth flow.
--
-- /api/calendar/connect (authenticated) issues a cryptographically strong,
-- SINGLE-USE, short-lived state value bound server-side to the authenticated
-- user. The Google browser redirect hits /api/calendar/callback WITHOUT a JWT;
-- the backend authenticates the request purely by resolving this random state
-- and then deletes it (single-use). No userId is trusted from query params and
-- no JWT travels through the OAuth redirect.
--
-- Flyway note: V4 was never created (numbering gap); V5 added calendar sync
-- adjustments. This is the next sequential migration.
-- =============================================================================

CREATE TABLE calendar_oauth_states (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    state      VARCHAR(128) NOT NULL,
    user_id    UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_oauth_state_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_oauth_state UNIQUE (state)
);

CREATE INDEX idx_oauth_state_expires ON calendar_oauth_states (expires_at);
