package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    Optional<Consultation> findByAppointmentId(UUID appointmentId);

    List<Consultation> findByDoctorId(UUID doctorId);

    boolean existsByAppointmentId(UUID appointmentId);
}
