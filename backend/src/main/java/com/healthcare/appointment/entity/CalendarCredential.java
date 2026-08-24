package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores the minimum required Google OAuth 2.0 state per user:
 * refresh token, current access token, and its expiry.
 *
 * SECURITY: tokens are never returned through APIs, never logged.
 * One row per user (unique constraint uq_credentials_user).
 */
@Entity
@Table(name = "calendar_credentials")
@Getter
@Setter
public class CalendarCredential extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Long-lived; used to mint new access tokens. Null until Google grants offline access. */
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    /** Short-lived; refreshed transparently by the client before API calls. */
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;
}
