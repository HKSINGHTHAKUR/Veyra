package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.CalendarStatusResponse;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.integration.calendar.CalendarException;
import com.healthcare.appointment.service.CalendarService;
import com.healthcare.appointment.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * Google Calendar OAuth 2.0 endpoints.
 *
 * Browser-compatible flow (no JWT travels through the Google redirect):
 *   1. GET /connect (authenticated)  → mints a server-side SINGLE-USE state bound
 *      to the authenticated user and returns the Google consent URL.
 *   2. Google redirects the browser to GET /callback?code&state with NO auth header.
 *      This endpoint is intentionally public; the request is authenticated by
 *      resolving the random single-use state to its user server-side.
 *      Success redirects the browser to the frontend; failures return a safe 400.
 *
 * Tokens/secrets are never returned or logged. userId is never trusted from
 * query parameters.
 */
@Slf4j
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final CurrentUserService currentUserService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /** Step 1: returns the Google consent URL (frontend redirects the browser there). */
    @GetMapping("/connect")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> connect() {
        String authorizationUrl = calendarService.startConnection(currentUserService.getCurrentUser());
        return ResponseEntity.ok(Map.of("authorizationUrl", authorizationUrl));
    }

    /**
     * Step 2: Google redirects here WITHOUT a JWT. Public by design (see SecurityConfig);
     * the caller is identified exclusively by consuming the single-use state.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {

        var user = calendarService.consumeState(state); // 400 on unknown/expired/replayed

        if (code == null || code.isBlank()) {
            throw new BadRequestException("Missing authorization code");
        }
        try {
            calendarService.completeConnection(user, code);
        } catch (CalendarException e) {
            // Safe message only; no provider internals
            throw new BadRequestException("Google connection failed");
        }
        return ResponseEntity.status(302)
                .location(URI.create(frontendUrl + "/calendar/connected?status=success"))
                .build();
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CalendarStatusResponse> status() {
        return ResponseEntity.ok(calendarService.getStatus(currentUserService.getCurrentUser()));
    }

    @PostMapping("/disconnect")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> disconnect() {
        calendarService.disconnect(currentUserService.getCurrentUser());
        return ResponseEntity.ok(Map.of("status", "DISCONNECTED"));
    }
}
