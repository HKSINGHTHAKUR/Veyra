package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Optional<Doctor> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<Doctor> findByActive(boolean active);

    List<Doctor> findBySpecialisationIgnoreCaseAndActive(String specialisation, boolean active);

    List<Doctor> findBySpecialisationContainingIgnoreCaseAndActive(String keyword, boolean active);
}
