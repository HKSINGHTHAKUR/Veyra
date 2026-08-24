package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.SlotHold;
import com.healthcare.appointment.entity.SlotHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotHoldRepository extends JpaRepository<SlotHold, UUID> {

    Optional<SlotHold> findByDoctorIdAndSlotStartAndStatus(
            UUID doctorId, Instant slotStart, SlotHoldStatus status);

    List<SlotHold> findByPatientIdAndStatus(UUID patientId, SlotHoldStatus status);

    /** Used by the expiry scheduler to find holds that have timed out. */
    List<SlotHold> findByStatusAndExpiresAtBefore(SlotHoldStatus status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM SlotHold h WHERE h.id = :id")
    Optional<SlotHold> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Used by AvailabilityService to exclude held slots from availability results.
     * Only ACTIVE and non-expired holds block a slot; EXPIRED/RELEASED/CONVERTED holds are ignored.
     * An ACTIVE hold that has passed its expiresAt is treated as expired and does not block.
     */
    @Query("""
            SELECT h FROM SlotHold h
            WHERE h.doctor.id = :doctorId
              AND h.slotStart >= :from
              AND h.slotStart < :to
              AND h.status = :status
              AND h.expiresAt > :now
            """)
    List<SlotHold> findActiveByDoctorAndDateRange(
            @Param("doctorId") UUID doctorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") SlotHoldStatus status,
            @Param("now") Instant now);

    /**
     * Bulk-expires all ACTIVE holds past their expiry time.
     * Called by the hold-expiry scheduler in Phase 7.
     */
    @Modifying
    @Query("""
            UPDATE SlotHold h
            SET h.status = :expiredStatus
            WHERE h.status = :activeStatus
              AND h.expiresAt < :now
            """)
    int expireStaleHolds(
            @Param("now") Instant now,
            @Param("activeStatus") SlotHoldStatus activeStatus,
            @Param("expiredStatus") SlotHoldStatus expiredStatus);
}
