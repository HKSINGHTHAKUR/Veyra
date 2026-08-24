package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the Google Calendar synchronisation state for an appointment.
 * OAuth credentials are stored separately (Phase 8).
 * One sync record per appointment per user (patient or doctor each get their own event).
 */
@Entity
@Table(name = "calendar_syncs")
@Getter
@Setter
public class CalendarSync extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The event ID returned by the Google Calendar API after creation. */
    @Column(name = "google_event_id", length = 255)
    private String googleEventId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CalendarSyncStatus status = CalendarSyncStatus.PENDING;

    @Column(name = "last_sync_attempt_at")
    private Instant lastSyncAttemptAt;

    /** Bounded retry counter, incremented each time the worker claims this sync. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
