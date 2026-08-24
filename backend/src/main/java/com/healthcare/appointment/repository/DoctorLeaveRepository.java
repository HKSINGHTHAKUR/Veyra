package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, UUID> {

    List<DoctorLeave> findByDoctorId(UUID doctorId);

    List<DoctorLeave> findByDoctorIdAndLeaveDateBetween(UUID doctorId, LocalDate from, LocalDate to);

    Optional<DoctorLeave> findByDoctorIdAndLeaveDate(UUID doctorId, LocalDate leaveDate);

    boolean existsByDoctorIdAndLeaveDate(UUID doctorId, LocalDate leaveDate);
}
