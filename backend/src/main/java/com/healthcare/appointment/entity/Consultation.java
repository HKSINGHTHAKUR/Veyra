package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Clinical notes recorded by the doctor after seeing the patient.
 * One consultation per appointment (enforced by the unique constraint).
 * Prescription items are stored separately and linked to this consultation.
 */
@Entity
@Table(name = "consultations")
@Getter
@Setter
public class Consultation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @NotBlank
    @Column(name = "clinical_notes", nullable = false, columnDefinition = "TEXT")
    private String clinicalNotes;

    @Column(name = "follow_up_instructions", columnDefinition = "TEXT")
    private String followUpInstructions;

    @NotNull
    @Column(name = "consultation_timestamp", nullable = false)
    private Instant consultationTimestamp;
}
