package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A confirmed booking between a patient and a doctor.
 *
 * Double-booking prevention is enforced by the database via a partial unique
 * index on (doctor_id, appointment_start) WHERE status IN ('BOOKED','RESCHEDULED').
 * This guarantees that even under concurrent requests, the same slot cannot be
 * double-booked. The application-level booking service (Phase 4) will also use
 * pessimistic locking on SlotHold rows as a first line of defence.
 *
 * rescheduledFromId stores the previous appointment's UUID when this appointment
 * was created as a reschedule — stored as a plain UUID to avoid self-referential
 * JPA complexity.
 */
@Entity
@Table(name = "appointments")
@Getter
@Setter
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /** The hold that was converted to create this appointment; may be null. */
    @Column(name = "slot_hold_id")
    private UUID slotHoldId;

    @NotNull
    @Column(name = "appointment_start", nullable = false)
    private Instant appointmentStart;

    @NotNull
    @Column(name = "appointment_end", nullable = false)
    private Instant appointmentEnd;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    @Column(name = "booked_at", nullable = false, updatable = false)
    private Instant bookedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /** UUID of the appointment this one replaced, if it was created via reschedule. */
    @Column(name = "rescheduled_from_id")
    private UUID rescheduledFromId;

    @PrePersist
    protected void onCreate() {
        bookedAt = Instant.now();
    }
}
