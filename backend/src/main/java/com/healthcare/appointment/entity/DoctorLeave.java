package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a single day on which a doctor is unavailable.
 * The unique constraint on (doctor_id, leave_date) prevents duplicate entries.
 * Appointments already booked on a leave date are handled by business logic
 * in a later phase — this entity only records the leave fact.
 */
@Entity
@Table(name = "doctor_leaves")
@Getter
@Setter
public class DoctorLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @NotNull
    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
