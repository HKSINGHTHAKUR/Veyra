package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.Notification;
import com.healthcare.appointment.entity.NotificationStatus;
import com.healthcare.appointment.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserId(UUID userId);

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByAppointmentId(UUID appointmentId);

    List<Notification> findByUserIdAndType(UUID userId, NotificationType type);

    boolean existsByAppointmentIdAndType(UUID appointmentId, NotificationType type);

    /**
     * Atomic worker claim: flips status and bumps attemptCount in one conditional
     * UPDATE, so two competing workers can never both claim the same notification
     * (the loser matches 0 rows). clearAutomatically evicts the stale pre-claim
     * entity from the persistence context so the caller reloads fresh state.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE Notification n SET n.status = :newStatus, n.lastAttemptAt = :now, n.attemptCount = n.attemptCount + 1 WHERE n.id = :id AND n.status = :expected")
    int claim(@org.springframework.data.repository.query.Param("id") UUID id,
              @org.springframework.data.repository.query.Param("expected") NotificationStatus expected,
              @org.springframework.data.repository.query.Param("newStatus") NotificationStatus newStatus,
              @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    /**
     * Retrieves notifications eligible for retry, ordered oldest-first.
     * The retry scheduler (Phase 7) uses this to process failed/pending sends.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.status IN :retryableStatuses
              AND n.attemptCount < :maxAttempts
            ORDER BY n.createdAt ASC
            """)
    List<Notification> findRetryable(
            @Param("retryableStatuses") List<NotificationStatus> retryableStatuses,
            @Param("maxAttempts") int maxAttempts);
}
