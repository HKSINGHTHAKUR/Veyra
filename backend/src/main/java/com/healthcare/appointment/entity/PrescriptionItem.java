package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A single prescribed medication within a consultation.
 * A consultation can have multiple prescription items.
 */
@Entity
@Table(name = "prescription_items")
@Getter
@Setter
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @NotBlank
    @Column(name = "medication_name", nullable = false, length = 255)
    private String medicationName;

    @NotBlank
    @Column(name = "dosage", nullable = false, length = 100)
    private String dosage;

    @NotBlank
    @Column(name = "frequency", nullable = false, length = 100)
    private String frequency;

    @Min(1)
    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;
}
