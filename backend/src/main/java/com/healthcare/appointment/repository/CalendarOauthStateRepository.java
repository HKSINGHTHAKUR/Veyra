package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.CalendarOauthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CalendarOauthStateRepository extends JpaRepository<CalendarOauthState, UUID> {

    Optional<CalendarOauthState> findByState(String state);

    void deleteByUserId(UUID userId);
}
