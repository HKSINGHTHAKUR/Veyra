package com.healthcare.appointment.integration.calendar;

import java.time.Instant;

/**
 * Low-level Google Calendar provider abstraction.
 * Business code depends on CalendarService, never on this or the Google SDK.
 * Access tokens are supplied by the caller (CalendarService resolves/refreshes them),
 * keeping this client stateless and trivially fakeable in tests.
 */
public interface GoogleCalendarClient {

    /** Builds the Google OAuth 2.0 consent screen URL for the given state parameter. */
    String buildAuthorizationUrl(String state);

    /** Exchanges an authorization code for tokens. Never surfaces raw HTTP details. */
    TokenPair exchangeCode(String code) throws CalendarException;

    /** Mints a new access token from a stored refresh token. */
    TokenPair refreshAccessToken(String refreshToken) throws CalendarException;

    /** Creates a calendar event; returns the Google event ID. */
    String createEvent(String accessToken, CalendarEventDetails event) throws CalendarException;

    /** Updates an existing event by ID. */
    void updateEvent(String accessToken, String eventId, CalendarEventDetails event) throws CalendarException;

    /** Deletes an existing event by ID. */
    void deleteEvent(String accessToken, String eventId) throws CalendarException;

    record TokenPair(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {}

    record CalendarEventDetails(
            String title,
            String description,
            Instant startUtc,
            Instant endUtc) {
        public String startRfc3339() { return toRfc3339(startUtc); }
        public String endRfc3339()   { return toRfc3339(endUtc); }

        /** Single deliberate timezone decision: UTC everywhere (matches project time model). */
        private static String toRfc3339(Instant i) {
            return i.toString(); // Instant.toString() is RFC-3339 UTC ('Z' suffix)
        }
    }
}
