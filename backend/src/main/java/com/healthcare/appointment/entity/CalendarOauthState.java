package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, short-lived OAuth state binding a random value to the user who
 * started the Google connection. Enables the browser callback to authenticate
 * WITHOUT a JWT: the callback resolves the user exclusively from this stored
 * random state (never from query parameters). The row is deleted on
 * consumption, making replay impossible.
 */
@Entity
@Table(name = "calendar_oauth_states")
@Getter
@Setter
public class CalendarOauthState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Cryptographically strong random value sent to Google and verified on return. */
    @NotNull
    @Column(name = "state", nullable = false, length = 128, unique = true)
    private String state;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
