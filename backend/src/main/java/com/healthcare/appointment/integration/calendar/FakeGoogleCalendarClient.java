package com.healthcare.appointment.integration.calendar;

import lombok.Getter;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic in-memory Google client for tests. No network, no credentials.
 * Configurable failure modes per operation; mirrors what a real calendar contains.
 */
@Component
@Profile("test")
@Primary
public class FakeGoogleCalendarClient implements GoogleCalendarClient {

    private final AtomicBoolean failCreate = new AtomicBoolean(false);
    private final AtomicBoolean failUpdate = new AtomicBoolean(false);
    private final AtomicBoolean failDelete = new AtomicBoolean(false);

    /** eventId -> details; simulates the remote calendar. */
    @Getter
    private final Map<String, CalendarEventDetails> events = new HashMap<>();

    @Getter
    private final List<String> deletedEventIds = new ArrayList<>();

    @Getter
    private int createCount = 0;
    @Getter
    private int updateCount = 0;
    @Getter
    private int deleteCount = 0;

    public void setFailCreate(boolean b) { failCreate.set(b); }
    public void setFailUpdate(boolean b) { failUpdate.set(b); }
    public void setFailDelete(boolean b) { failDelete.set(b); }

    public void reset() {
        events.clear();
        deletedEventIds.clear();
        createCount = 0;
        updateCount = 0;
        deleteCount = 0;
        failCreate.set(false);
        failUpdate.set(false);
        failDelete.set(false);
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=test-client-id"
                + "&redirect_uri=http://localhost:8080/api/calendar/callback"
                + "&response_type=code&scope=https://www.googleapis.com/auth/calendar.events"
                + "&access_type=offline&prompt=consent&state=" + state;
    }

    @Override
    public TokenPair exchangeCode(String code) throws CalendarException {
        if (code == null || code.isBlank()) throw new CalendarException("Empty authorization code");
        return new TokenPair("fake-access-" + UUID.randomUUID(), "fake-refresh-" + UUID.randomUUID(),
                Instant.now().plusSeconds(3600));
    }

    @Override
    public TokenPair refreshAccessToken(String refreshToken) throws CalendarException {
        if (refreshToken == null || refreshToken.isBlank()) throw new CalendarException("No refresh token");
        return new TokenPair("fake-access-" + UUID.randomUUID(), refreshToken,
                Instant.now().plusSeconds(3600));
    }

    @Override
    public String createEvent(String accessToken, CalendarEventDetails event) throws CalendarException {
        requireToken(accessToken);
        if (failCreate.get()) throw new CalendarException("Fake create failure");
        String id = "evt-" + UUID.randomUUID();
        events.put(id, event);
        createCount++;
        return id;
    }

    @Override
    public void updateEvent(String accessToken, String eventId, CalendarEventDetails event) throws CalendarException {
        requireToken(accessToken);
        if (failUpdate.get()) throw new CalendarException("Fake update failure");
        if (!events.containsKey(eventId)) throw new CalendarException("Event not found: " + eventId);
        events.put(eventId, event);
        updateCount++;
    }

    @Override
    public void deleteEvent(String accessToken, String eventId) throws CalendarException {
        requireToken(accessToken);
        if (failDelete.get()) throw new CalendarException("Fake delete failure");
        events.remove(eventId);
        deletedEventIds.add(eventId);
        deleteCount++;
    }

    private void requireToken(String token) throws CalendarException {
        if (token == null || token.isBlank()) throw new CalendarException("Missing access token");
    }
}
