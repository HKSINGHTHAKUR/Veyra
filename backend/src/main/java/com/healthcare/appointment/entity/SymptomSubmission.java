package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores patient-reported symptoms submitted before an appointment.
 *
 * Phase 4: symptoms are submitted at HOLD stage before an Appointment exists.
 * Therefore appointment is nullable initially; slotHold holds the pre-confirmation link.
 * After confirmation, appointment is set (one submission per appointment enforced by unique
 * partial index, and one per hold).
 */
@Entity
@Table(name = "symptom_submissions")
@Getter
@Setter
public class SymptomSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "slot_hold_id")
    private SlotHold slotHold;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotBlank
    @Column(name = "symptom_text", nullable = false, columnDefinition = "TEXT")
    private String symptomText;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @PrePersist
    protected void onCreate() {
        submittedAt = Instant.now();
    }
}
