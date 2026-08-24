package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
