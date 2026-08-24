package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Temporarily reserves a slot for a patient while they complete booking.
 *
 * Concurrency safety: a partial unique index on (doctor_id, slot_start)
 * WHERE status = 'ACTIVE' ensures only one patient can hold any given slot
 * at a time. The Phase 4 booking service will use SELECT FOR UPDATE on this
 * row before converting the hold to a confirmed appointment.
 *
 * Hold lifecycle: ACTIVE → CONVERTED (on booking) or RELEASED/EXPIRED.
 */
@Entity
@Table(name = "slot_holds")
@Getter
@Setter
public class SlotHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Column(name = "slot_start", nullable = false)
    private Instant slotStart;

    @NotNull
    @Column(name = "slot_end", nullable = false)
    private Instant slotEnd;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlotHoldStatus status = SlotHoldStatus.ACTIVE;

    @Column(name = "held_at", nullable = false, updatable = false)
    private Instant heldAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        heldAt = Instant.now();
    }
}
