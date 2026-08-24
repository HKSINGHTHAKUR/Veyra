package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Defines a doctor's recurring weekly availability for one day of the week.
 * Slot start times are derived on demand from startTime + slotDurationMinutes.
 * The unique constraint on (doctor_id, day_of_week) means one schedule row per day.
 */
@Entity
@Table(name = "doctor_working_hours")
@Getter
@Setter
public class DoctorWorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Min(1)
    @Max(480)
    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes = 30;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
