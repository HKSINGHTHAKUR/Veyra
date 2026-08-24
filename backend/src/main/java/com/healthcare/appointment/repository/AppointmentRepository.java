package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.Appointment;
import com.healthcare.appointment.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByPatientId(UUID patientId);

    List<Appointment> findByPatientIdAndStatus(UUID patientId, AppointmentStatus status);

    List<Appointment> findByDoctorId(UUID doctorId);

    List<Appointment> findByDoctorIdAndStatus(UUID doctorId, AppointmentStatus status);

    List<Appointment> findByPatientIdOrderByBookedAtDesc(UUID patientId);

    List<Appointment> findByDoctorIdOrderByAppointmentStartAsc(UUID doctorId);

    List<Appointment> findByDoctorIdAndAppointmentStartBetween(UUID doctorId, Instant from, Instant to);

    List<Appointment> findByAppointmentStartBetweenAndStatusIn(Instant from, Instant to, List<AppointmentStatus> statuses);

    /**
     * Checks whether a non-cancelled appointment already occupies this doctor+time.
     * Used by the availability check before attempting a hold or booking.
     */
    boolean existsByDoctorIdAndAppointmentStartAndStatusIn(
            UUID doctorId, Instant appointmentStart, List<AppointmentStatus> statuses);

    /**
     * Finds all active appointments for a doctor within a time window.
     * Used by the leave conflict check in Phase 3/7.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.appointmentStart >= :from
              AND a.appointmentStart < :to
              AND a.status IN :statuses
            """)
    List<Appointment> findByDoctorAndTimeRangeAndStatusIn(
            @Param("doctorId") UUID doctorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("statuses") List<AppointmentStatus> statuses);

    Optional<Appointment> findByDoctorIdAndAppointmentStartAndStatusIn(
            UUID doctorId, Instant appointmentStart, List<AppointmentStatus> statuses);
}
