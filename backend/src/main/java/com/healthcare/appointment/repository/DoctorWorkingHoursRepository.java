package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.DoctorWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorWorkingHoursRepository extends JpaRepository<DoctorWorkingHours, UUID> {

    List<DoctorWorkingHours> findByDoctorId(UUID doctorId);

    List<DoctorWorkingHours> findByDoctorIdAndActive(UUID doctorId, boolean active);

    Optional<DoctorWorkingHours> findByDoctorIdAndDayOfWeek(UUID doctorId, DayOfWeek dayOfWeek);
}
