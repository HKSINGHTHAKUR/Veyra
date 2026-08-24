package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.CalendarCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CalendarCredentialRepository extends JpaRepository<CalendarCredential, UUID> {

    Optional<CalendarCredential> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
