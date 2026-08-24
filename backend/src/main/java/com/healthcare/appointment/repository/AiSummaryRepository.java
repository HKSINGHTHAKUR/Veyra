package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.AiSummary;
import com.healthcare.appointment.entity.AiSummaryStatus;
import com.healthcare.appointment.entity.AiSummaryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSummaryRepository extends JpaRepository<AiSummary, UUID> {

    Optional<AiSummary> findByAppointmentIdAndType(UUID appointmentId, AiSummaryType type);

    List<AiSummary> findByStatus(AiSummaryStatus status);

    boolean existsByAppointmentIdAndType(UUID appointmentId, AiSummaryType type);
}
