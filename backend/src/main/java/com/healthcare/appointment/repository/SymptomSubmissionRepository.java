package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.SymptomSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SymptomSubmissionRepository extends JpaRepository<SymptomSubmission, UUID> {

    Optional<SymptomSubmission> findByAppointmentId(UUID appointmentId);

    Optional<SymptomSubmission> findBySlotHoldId(UUID slotHoldId);

    boolean existsByAppointmentId(UUID appointmentId);

    boolean existsBySlotHoldId(UUID slotHoldId);
}
