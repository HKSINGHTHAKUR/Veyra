package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.CalendarSync;
import com.healthcare.appointment.entity.CalendarSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarSyncRepository extends JpaRepository<CalendarSync, UUID> {

    Optional<CalendarSync> findByAppointmentIdAndUserId(UUID appointmentId, UUID userId);

    List<CalendarSync> findByAppointmentId(UUID appointmentId);

    List<CalendarSync> findByStatus(CalendarSyncStatus status);

    List<CalendarSync> findByStatusIn(List<CalendarSyncStatus> statuses);
}
