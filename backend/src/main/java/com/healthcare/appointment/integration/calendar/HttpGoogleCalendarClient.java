package com.healthcare.appointment.integration.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Real Google Calendar client (prod profile). Talks to Google OAuth 2.0 and
 * Calendar REST v3 endpoints via RestTemplate. No Google SDK dependency.
 *
 * SECURITY: client secret and tokens are never logged; failures surface as
 * CalendarException with a safe message.
 * TIMEZONE: all times are UTC Instants (RFC-3339 'Z') – single deliberate
 * decision, matching the project's UTC time model.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class HttpGoogleCalendarClient implements GoogleCalendarClient {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    public static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";

    private final ObjectMapper objectMapper;

    @Value("${app.google.calendar.client-id}")
    private String clientId;

    @Value("${app.google.calendar.client-secret}")
    private String clientSecret;

    @Value("${app.google.calendar.redirect-uri}")
    private String redirectUri;

    private final RestTemplate rest =
            new RestTemplate(new JdkClientHttpRequestFactory());

    @Override
    public String buildAuthorizationUrl(String state) {
        return AUTH_URL + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + SCOPE
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + state;
    }

    @Override
    public TokenPair exchangeCode(String code) throws CalendarException {
        try {
            MultiValueMap<String, String> form = tokenForm();
            form.add("code", code);
            form.add("grant_type", "authorization_code");
            JsonNode node = postToken(form);
            return new TokenPair(
                    text(node, "access_token"),
                    text(node, "refresh_token"), // may be absent if previously granted
                    Instant.now().plusSeconds(longOf(node, "expires_in", 3600)));
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarException("Token exchange failed", e);
        }
    }

    @Override
    public TokenPair refreshAccessToken(String refreshToken) throws CalendarException {
        try {
            MultiValueMap<String, String> form = tokenForm();
            form.add("refresh_token", refreshToken);
            form.add("grant_type", "refresh_token");
            JsonNode node = postToken(form);
            return new TokenPair(
                    text(node, "access_token"),
                    refreshToken,
                    Instant.now().plusSeconds(longOf(node, "expires_in", 3600)));
        } catch (Exception e) {
            throw new CalendarException("Token refresh failed", e);
        }
    }

    @Override
    public String createEvent(String accessToken, CalendarEventDetails event) throws CalendarException {
        ResponseEntity<String> resp = call(HttpMethod.POST, EVENTS_URL, accessToken, eventJson(event));
        try {
            return objectMapper.readTree(resp.getBody()).path("id").asText(null);
        } catch (Exception e) {
            throw new CalendarException("Failed to parse created event id", e);
        }
    }

    @Override
    public void updateEvent(String accessToken, String eventId, CalendarEventDetails event) throws CalendarException {
        call(HttpMethod.PATCH, EVENTS_URL + "/" + eventId, accessToken, eventJson(event));
    }

    @Override
    public void deleteEvent(String accessToken, String eventId) throws CalendarException {
        call(HttpMethod.DELETE, EVENTS_URL + "/" + eventId, accessToken, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private MultiValueMap<String, String> tokenForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        return form;
    }

    private JsonNode postToken(MultiValueMap<String, String> form) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> resp =
                rest.postForEntity(TOKEN_URL, new HttpEntity<>(form, headers), String.class);
        return objectMapper.readTree(resp.getBody());
    }

    private ResponseEntity<String> call(HttpMethod method, String url, String accessToken, String json)
            throws CalendarException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            ResponseEntity<String> resp = rest.exchange(url, method, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new CalendarException("Google API returned " + resp.getStatusCode());
            }
            return resp;
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            // Never include response body/tokens in the message
            throw new CalendarException("Google API call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private String eventJson(CalendarEventDetails e) throws CalendarException {
        try {
            var root = objectMapper.createObjectNode();
            root.put("summary", e.title());
            if (e.description() != null) root.put("description", e.description());
            var start = root.putObject("start");
            start.put("dateTime", e.startRfc3339());
            start.put("timeZone", "UTC");
            var end = root.putObject("end");
            end.put("dateTime", e.endRfc3339());
            end.put("timeZone", "UTC");
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new CalendarException("Failed to serialise event", ex);
        }
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        if (v == null || v.isBlank()) throw new IllegalStateException("Missing field in token response");
        return v;
    }

    private long longOf(JsonNode node, String field, long dflt) {
        return node.hasNonNull(field) ? node.path(field).asLong(dflt) : dflt;
    }
}
