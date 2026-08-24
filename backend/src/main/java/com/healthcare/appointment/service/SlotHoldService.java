package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.HoldResponse;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.exception.ConflictException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotHoldService {

    private static final List<AppointmentStatus> BLOCKING = List.of(AppointmentStatus.BOOKED, AppointmentStatus.RESCHEDULED);

    private final DoctorRepository doctorRepository;
    private final DoctorWorkingHoursRepository workingHoursRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotHoldRepository slotHoldRepository;
    private final CurrentUserService currentUserService;
    private final AvailabilityService availabilityService;

    @Value("${app.slot.hold-duration-minutes:10}")
    private int holdDurationMinutes;

    @Transactional
    public HoldResponse createHold(UUID doctorId, Instant slotStart) {
        Patient patient = currentUserService.getCurrentPatient();

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        if (!doctor.isActive()) {
            throw new BadRequestException("Doctor is not active");
        }
        if (slotStart == null) {
            throw new BadRequestException("slotStart is required");
        }
        Instant now = Instant.now();
        if (slotStart.isBefore(now)) {
            throw new BadRequestException("Cannot hold a past slot");
        }

        LocalDate date = slotStart.atZone(ZoneOffset.UTC).toLocalDate();
        // Derive slotEnd from working hours duration
        DoctorWorkingHours wh = workingHoursRepository.findByDoctorIdAndDayOfWeek(doctorId, date.getDayOfWeek())
                .orElseThrow(() -> new ConflictException("Doctor is not available on this day"));
        if (!wh.isActive()) {
            throw new ConflictException("Doctor is not available on this day");
        }
        Instant slotEnd = slotStart.plusSeconds(wh.getSlotDurationMinutes() * 60L);

        // Validate slot grid and duration
        if (!availabilityService.isValidSlot(doctorId, date, slotStart, slotEnd)) {
            throw new ConflictException("Slot is not on the doctor's valid schedule grid");
        }

        // Leave check
        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctorId, date)) {
            throw new ConflictException("Doctor is on leave for this date");
        }

        // Blocking appointment check
        boolean booked = appointmentRepository.existsByDoctorIdAndAppointmentStartAndStatusIn(doctorId, slotStart, BLOCKING);
        if (booked) {
            throw new ConflictException("Slot is already booked");
        }

        // Active hold check with expiry handling
        Optional<SlotHold> existingOpt = slotHoldRepository.findByDoctorIdAndSlotStartAndStatus(doctorId, slotStart, SlotHoldStatus.ACTIVE);
        if (existingOpt.isPresent()) {
            SlotHold existing = existingOpt.get();
            if (existing.getExpiresAt().isAfter(now)) {
                throw new ConflictException("Slot is currently held by another patient");
            } else {
                // Expired ACTIVE -> clean up
                existing.setStatus(SlotHoldStatus.EXPIRED);
                slotHoldRepository.save(existing);
            }
        }

        // Insert new hold – unique index is final guard
        SlotHold hold = new SlotHold();
        hold.setDoctor(doctor);
        hold.setPatient(patient);
        hold.setSlotStart(slotStart);
        hold.setSlotEnd(slotEnd);
        hold.setStatus(SlotHoldStatus.ACTIVE);
        hold.setExpiresAt(now.plusSeconds(holdDurationMinutes * 60L));

        try {
            hold = slotHoldRepository.saveAndFlush(hold);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Slot is no longer available (concurrent hold)");
        }

        return new HoldResponse(hold);
    }

    @Transactional(readOnly = true)
    public HoldResponse getHold(UUID holdId) {
        SlotHold hold = slotHoldRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold", holdId));
        // ADMIN may view any hold
        try {
            var user = currentUserService.getCurrentUser();
            if (user != null && "ADMIN".equals(user.getRole().name())) {
                return new HoldResponse(hold);
            }
        } catch (Exception ignored) {}
        // Owning patient may view
        try {
            Patient currentPatient = currentUserService.getCurrentPatient();
            if (hold.getPatient().getId().equals(currentPatient.getId())) {
                return new HoldResponse(hold);
            }
        } catch (Exception ignored) {}
        throw new org.springframework.security.access.AccessDeniedException("Access denied");
    }
}
