package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.AvailabilityResponse;
import com.healthcare.appointment.dto.SlotDto;
import com.healthcare.appointment.entity.AppointmentStatus;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.DoctorWorkingHours;
import com.healthcare.appointment.entity.SlotHoldStatus;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.DoctorLeaveRepository;
import com.healthcare.appointment.repository.DoctorRepository;
import com.healthcare.appointment.repository.DoctorWorkingHoursRepository;
import com.healthcare.appointment.repository.SlotHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calculates doctor availability dynamically from:
 *   working hours − doctor leaves − BOOKED/RESCHEDULED appointments − ACTIVE slot holds
 *
 * No slot rows are pre-generated or persisted. Availability is always derived
 * from the current state of the four data sources above.
 *
 * SLOT-BOUNDARY INVARIANT (critical for Phase 4):
 *   A valid slot start is any time T in [startTime, endTime) such that
 *   T = startTime + N × slotDuration for some integer N ≥ 0
 *   AND T + slotDuration ≤ endTime.
 *
 *   Any other start time is invalid. The booking service (Phase 4) MUST call
 *   isValidSlot() before inserting an appointment to enforce this invariant,
 *   which in turn makes the partial unique index on (doctor_id, appointment_start)
 *   the correct concurrency safety key.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final List<AppointmentStatus> BLOCKING_STATUSES =
            List.of(AppointmentStatus.BOOKED, AppointmentStatus.RESCHEDULED);

    private final DoctorRepository doctorRepository;
    private final DoctorWorkingHoursRepository workingHoursRepository;
    private final DoctorLeaveRepository leaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotHoldRepository slotHoldRepository;

    /**
     * Returns all non-past candidate slots for a doctor on a given date,
     * annotated with their availability status.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        if (!doctor.isActive()) {
            return AvailabilityResponse.empty(doctorId, date);
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        Optional<DoctorWorkingHours> whOpt =
                workingHoursRepository.findByDoctorIdAndDayOfWeek(doctorId, dayOfWeek);

        if (whOpt.isEmpty() || !whOpt.get().isActive()) {
            return AvailabilityResponse.empty(doctorId, date);
        }

        if (leaveRepository.existsByDoctorIdAndLeaveDate(doctorId, date)) {
            return AvailabilityResponse.empty(doctorId, date);
        }

        DoctorWorkingHours wh = whOpt.get();
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Load booked/rescheduled appointment starts for this doctor+date window
        Set<Instant> bookedStarts = appointmentRepository
                .findByDoctorAndTimeRangeAndStatusIn(doctorId, dayStart, dayEnd, BLOCKING_STATUSES)
                .stream()
                .map(a -> a.getAppointmentStart())
                .collect(Collectors.toSet());

        Instant now      = Instant.now();

        // Load active hold starts for this doctor+date window - only non-expired ACTIVE holds block
        Set<Instant> heldStarts = slotHoldRepository
                .findActiveByDoctorAndDateRange(doctorId, dayStart, dayEnd, SlotHoldStatus.ACTIVE, now)
                .stream()
                .map(h -> h.getSlotStart())
                .collect(Collectors.toSet());
        int     duration = wh.getSlotDurationMinutes();
        List<SlotDto> slots = new ArrayList<>();

        LocalTime current = wh.getStartTime();
        while (!current.plusMinutes(duration).isAfter(wh.getEndTime())) {
            Instant slotStart = date.atTime(current).toInstant(ZoneOffset.UTC);
            Instant slotEnd   = date.atTime(current.plusMinutes(duration)).toInstant(ZoneOffset.UTC);

            // Exclude slots that have already started
            if (!slotStart.isBefore(now)) {
                boolean taken = bookedStarts.contains(slotStart) || heldStarts.contains(slotStart);
                slots.add(new SlotDto(slotStart, slotEnd, !taken));
            }

            current = current.plusMinutes(duration);
        }

        return new AvailabilityResponse(doctorId, date, slots);
    }

    /**
     * Validates that slotStart falls exactly on a slot-grid boundary for this doctor,
     * and that slotEnd = slotStart + slotDuration.
     *
     * Phase 4 booking service MUST call this before creating an appointment.
     * This is the application-level enforcement of the slot-boundary invariant;
     * the partial unique DB index enforces it at the persistence level.
     *
     * @return true if the slot is on-grid and consistent; false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean isValidSlot(UUID doctorId, LocalDate date, Instant slotStart, Instant slotEnd) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        Optional<DoctorWorkingHours> whOpt =
                workingHoursRepository.findByDoctorIdAndDayOfWeek(doctorId, dayOfWeek);

        if (whOpt.isEmpty() || !whOpt.get().isActive()) {
            return false;
        }

        DoctorWorkingHours wh = whOpt.get();
        int duration = wh.getSlotDurationMinutes();

        // slotEnd must be exactly slotStart + duration
        if (!slotStart.plus(duration, ChronoUnit.MINUTES).equals(slotEnd)) {
            return false;
        }

        // slotStart must be one of the generated grid points
        LocalTime current = wh.getStartTime();
        while (!current.plusMinutes(duration).isAfter(wh.getEndTime())) {
            Instant candidate = date.atTime(current).toInstant(ZoneOffset.UTC);
            if (candidate.equals(slotStart)) {
                return true;
            }
            current = current.plusMinutes(duration);
        }

        return false;
    }
}
