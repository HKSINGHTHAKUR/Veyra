package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.CalendarStatusResponse;
import com.healthcare.appointment.entity.CalendarCredential;
import com.healthcare.appointment.entity.CalendarOauthState;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.integration.calendar.CalendarException;
import com.healthcare.appointment.integration.calendar.GoogleCalendarClient;
import com.healthcare.appointment.repository.CalendarCredentialRepository;
import com.healthcare.appointment.repository.CalendarOauthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Business-facing Google Calendar integration.
 *
 * OAuth (browser-compatible, no JWT in the redirect):
 *   1. Authenticated user calls /api/calendar/connect.
 *   2. Server mints a cryptographically strong SINGLE-USE state bound to that
 *      user (calendar_oauth_states, 10-minute expiry) and returns the Google
 *      consent URL containing it.
 *   3. Google redirects the browser to /api/calendar/callback?code&state with
 *      NO Authorization header. The server resolves the user purely from the
 *      stored random state (single-use: deleted on consumption; expired rows
 *      rejected). userId is never taken from query parameters.
 *   4. Code is exchanged; minimum tokens stored in calendar_credentials.
 *
 * Tokens are never returned through APIs and never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final int STATE_BYTES = 32;           // 256-bit
    private static final long STATE_TTL_MINUTES = 30;    // generous for consent flow

    private final CalendarCredentialRepository credentialRepository;
    private final CalendarOauthStateRepository oauthStateRepository;
    private final GoogleCalendarClient googleClient;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Starts a connection for the authenticated user. If this user already has a
     * still-valid OAuth state, that state is reused so duplicate/rapid Connect
     * clicks never invalidate an active flow (the old behaviour of deleting all
     * prior states first caused exactly that). Only expired states are cleaned up.
     */
    @Transactional
    public String startConnection(User user) {
        Instant now = Instant.now();

        CalendarOauthState state = oauthStateRepository
                .findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(user.getId(), now)
                .orElseGet(() -> {
                    CalendarOauthState row = new CalendarOauthState();
                    row.setState(randomHex());
                    row.setUser(user);
                    row.setExpiresAt(now.plus(STATE_TTL_MINUTES, ChronoUnit.MINUTES));
                    return oauthStateRepository.save(row);
                });

        return googleClient.buildAuthorizationUrl(state.getState());
    }

    private String randomHex() {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Resolves the browser callback's state to its user. Single-use: the state row
     * is deleted on successful consumption so a replayed callback fails. Expired or
     * unknown states are rejected with a safe message (no detail leakage).
     */
    @Transactional
    public User consumeState(String state) {
        if (state == null || state.isBlank()) {
            log.warn("OAuth callback rejected: state missing");
            throw new BadRequestException("Invalid or expired OAuth state");
        }
        CalendarOauthState row = oauthStateRepository.findByState(state)
                .orElseThrow(() -> {
                    log.warn("OAuth callback rejected: state not found");
                    return new BadRequestException("Invalid or expired OAuth state");
                });
        if (row.getExpiresAt().isBefore(Instant.now())) {
            log.warn("OAuth callback rejected: state expired (user {})", row.getUser().getId());
            oauthStateRepository.delete(row);
            throw new BadRequestException("Invalid or expired OAuth state");
        }
        User user = row.getUser();
        oauthStateRepository.delete(row); // single-use — a replayed callback fails
        return user;
    }

    /** Exchanges the authorization code and persists credentials for the authenticated user. */
    @Transactional
    public void completeConnection(User user, String code) throws CalendarException {
        GoogleCalendarClient.TokenPair tokens = googleClient.exchangeCode(code);

        CalendarCredential cred = credentialRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    CalendarCredential c = new CalendarCredential();
                    c.setUser(user);
                    return c;
                });
        // Keep existing refresh token if Google did not re-issue one
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            cred.setRefreshToken(tokens.refreshToken());
        }
        cred.setAccessToken(tokens.accessToken());
        cred.setAccessTokenExpiresAt(tokens.accessTokenExpiresAt());
        credentialRepository.save(cred);
        log.info("Google Calendar connected for user {}", user.getId());
    }

    @Transactional(readOnly = true)
    public CalendarStatusResponse getStatus(User user) {
        boolean connected = credentialRepository.existsByUserId(user.getId());
        return new CalendarStatusResponse(connected, user.getId());
    }

    @Transactional
    public void disconnect(User user) {
        credentialRepository.findByUserId(user.getId()).ifPresent(credentialRepository::delete);
        log.info("Google Calendar disconnected for user {}", user.getId());
    }

    /**
     * Returns a valid access token for the user, refreshing transparently if expired.
     * Returns null when the user has not connected their calendar.
     */
    @Transactional
    public String getValidAccessToken(User user) throws CalendarException {
        CalendarCredential cred = credentialRepository.findByUserId(user.getId()).orElse(null);
        if (cred == null || cred.getRefreshToken() == null) return null;

        boolean needsRefresh = cred.getAccessToken() == null
                || cred.getAccessTokenExpiresAt() == null
                || cred.getAccessTokenExpiresAt().isBefore(java.time.Instant.now().plusSeconds(60));

        if (needsRefresh) {
            GoogleCalendarClient.TokenPair refreshed =
                    googleClient.refreshAccessToken(cred.getRefreshToken());
            cred.setAccessToken(refreshed.accessToken());
            cred.setAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
            if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
                cred.setRefreshToken(refreshed.refreshToken());
            }
            credentialRepository.save(cred);
        }
        return cred.getAccessToken();
    }
}
