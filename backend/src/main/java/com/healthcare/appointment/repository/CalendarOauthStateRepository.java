package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.CalendarOauthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CalendarOauthStateRepository extends JpaRepository<CalendarOauthState, UUID> {

    Optional<CalendarOauthState> findByState(String state);

    /** Most recent still-valid state for a user, if any. */
    Optional<CalendarOauthState> findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(
            UUID userId, Instant expiresAt);

    void deleteByUserId(UUID userId);
}
