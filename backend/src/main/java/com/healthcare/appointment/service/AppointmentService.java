package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.AppointmentResponse;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.exception.ConflictException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.integration.email.EmailTemplateService;
import com.healthcare.appointment.repository.*;
import com.healthcare.appointment.event.AppointmentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final List<AppointmentStatus> BLOCKING = List.of(AppointmentStatus.BOOKED, AppointmentStatus.RESCHEDULED);

    private final AppointmentRepository appointmentRepository;
    private final SlotHoldRepository slotHoldRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorWorkingHoursRepository workingHoursRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final SymptomSubmissionRepository symptomRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final AvailabilityService availabilityService;
    private final ApplicationEventPublisher eventPublisher;
    private final CalendarSyncService calendarSyncService;

    @Transactional
    public AppointmentResponse confirmHold(UUID holdId) {
        Patient patient = currentUserService.getCurrentPatient();
        Instant now = Instant.now();

        SlotHold hold = slotHoldRepository.findByIdForUpdate(holdId)
                .orElse(slotHoldRepository.findById(holdId).orElseThrow(() -> new ResourceNotFoundException("Hold", holdId)));

        if (!hold.getPatient().getId().equals(patient.getId())) {
            throw new ResourceNotFoundException("Hold", holdId);
        }
        if (hold.getStatus() != SlotHoldStatus.ACTIVE) {
            throw new ConflictException("Hold is not active");
        }
        if (hold.getExpiresAt().isBefore(now) || hold.getExpiresAt().equals(now)) {
            hold.setStatus(SlotHoldStatus.EXPIRED);
            slotHoldRepository.save(hold);
            throw new ConflictException("Hold has expired");
        }

        Doctor doctor = hold.getDoctor();
        if (!doctor.isActive()) {
            throw new ConflictException("Doctor is not active");
        }
        LocalDate date = hold.getSlotStart().atZone(ZoneOffset.UTC).toLocalDate();
        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctor.getId(), date)) {
            throw new ConflictException("Doctor is on leave");
        }
        if (!availabilityService.isValidSlot(doctor.getId(), date, hold.getSlotStart(), hold.getSlotEnd())) {
            throw new ConflictException("Slot is no longer valid");
        }
        boolean booked = appointmentRepository.existsByDoctorIdAndAppointmentStartAndStatusIn(doctor.getId(), hold.getSlotStart(), BLOCKING);
        if (booked) {
            throw new ConflictException("Slot is already booked");
        }

        // Create appointment – unique index is final guard
        Appointment appt = new Appointment();
        appt.setPatient(patient);
        appt.setDoctor(doctor);
        appt.setAppointmentStart(hold.getSlotStart());
        appt.setAppointmentEnd(hold.getSlotEnd());
        appt.setStatus(AppointmentStatus.BOOKED);
        appt.setSlotHoldId(hold.getId());

        try {
            appt = appointmentRepository.saveAndFlush(appt);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Slot was booked concurrently");
        }

        // Link symptom if present
        Optional<SymptomSubmission> symptomOpt = symptomRepository.findBySlotHoldId(holdId);
        String symptomText = null;
        if (symptomOpt.isPresent()) {
            SymptomSubmission symptom = symptomOpt.get();
            symptom.setAppointment(appt);
            symptomRepository.save(symptom);
            symptomText = symptom.getSymptomText();
        }

        hold.setStatus(SlotHoldStatus.CONVERTED);
        slotHoldRepository.save(hold);

        // Notification record (pending, no email sent)
        Notification notif = new Notification();
        notif.setUser(patient.getUser());
        notif.setAppointmentId(appt.getId());
        notif.setType(NotificationType.BOOKING_CONFIRMATION);
        notif.setStatus(NotificationStatus.PENDING);
        notif.setSubject("Appointment confirmed");
        notif.setBody("Your appointment with Dr. " + doctor.getUser().getLastName()
                + " on " + EmailTemplateService.formatDate(date) + " is confirmed.");
        notificationRepository.save(notif);

        // Calendar sync records (DB only, PENDING) – Google API is called by the
        // background worker AFTER commit; booking never depends on Google availability.
        calendarSyncService.enqueueForParticipants(appt);

        // Publish pre-visit AI event AFTER_COMMIT – must not rollback booking on LLM failure
        eventPublisher.publishEvent(new AppointmentConfirmedEvent(this, appt.getId()));

        return new AppointmentResponse(appt, symptomText);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(UUID appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        enforceAccess(appt);
        String symptomText = symptomRepository.findByAppointmentId(appointmentId)
                .map(SymptomSubmission::getSymptomText).orElse(null);
        return new AppointmentResponse(appt, symptomText);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getPatientAppointments() {
        Patient patient = currentUserService.getCurrentPatient();
        return appointmentRepository.findByPatientIdOrderByBookedAtDesc(patient.getId())
                .stream().map(a -> {
                    String sym = symptomRepository.findByAppointmentId(a.getId()).map(SymptomSubmission::getSymptomText).orElse(null);
                    return new AppointmentResponse(a, sym);
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDoctorAppointments() {
        Doctor doctor = currentUserService.getCurrentDoctor();
        return appointmentRepository.findByDoctorIdOrderByAppointmentStartAsc(doctor.getId())
                .stream().map(a -> {
                    String sym = symptomRepository.findByAppointmentId(a.getId()).map(SymptomSubmission::getSymptomText).orElse(null);
                    return new AppointmentResponse(a, sym);
                }).toList();
    }

    @Transactional
    public AppointmentResponse cancelAppointment(UUID appointmentId, String reason) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        // Ownership: patient owner, doctor owner, or ADMIN
        try {
            Patient patient = currentUserService.getCurrentPatient();
            if (!appt.getPatient().getId().equals(patient.getId())) {
                // Not own patient appointment – check if admin/doctor
                checkDoctorOrAdmin(appt);
            }
        } catch (ResourceNotFoundException e) {
            // Not a patient – try doctor/admin
            checkDoctorOrAdmin(appt);
        }

        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ConflictException("Appointment is already cancelled");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Completed appointment cannot be cancelled");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setCancelledAt(Instant.now());
        appt.setCancellationReason(reason);
        appointmentRepository.save(appt);

        Notification notif = new Notification();
        notif.setUser(appt.getPatient().getUser());
        notif.setAppointmentId(appt.getId());
        notif.setType(NotificationType.CANCELLATION);
        notif.setStatus(NotificationStatus.PENDING);
        notif.setSubject("Appointment cancelled");
        notif.setBody("Your appointment on " + EmailTemplateService.formatAppointmentTime(appt.getAppointmentStart()) + " has been cancelled.");
        notificationRepository.save(notif);

        // Re-enqueue calendar syncs – worker sees CANCELLED and deletes Google events.
        calendarSyncService.enqueueForParticipants(appt);

        String sym = symptomRepository.findByAppointmentId(appt.getId()).map(SymptomSubmission::getSymptomText).orElse(null);
        return new AppointmentResponse(appt, sym);
    }

    @Transactional
    public AppointmentResponse rescheduleAppointment(UUID appointmentId, Instant newSlotStart) {
        // Only owning patient may reschedule
        Patient patient = currentUserService.getCurrentPatient();
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        if (!appt.getPatient().getId().equals(patient.getId())) {
            throw new ResourceNotFoundException("Appointment", appointmentId);
        }
        if (appt.getStatus() == AppointmentStatus.CANCELLED || appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Cannot reschedule a cancelled or completed appointment");
        }
        if (newSlotStart == null) {
            throw new BadRequestException("newSlotStart is required");
        }
        Instant now = Instant.now();
        if (newSlotStart.isBefore(now)) {
            throw new BadRequestException("Cannot reschedule to a past slot");
        }
        Doctor doctor = appt.getDoctor();
        if (!doctor.isActive()) {
            throw new ConflictException("Doctor is not active");
        }
        LocalDate date = newSlotStart.atZone(ZoneOffset.UTC).toLocalDate();
        DoctorWorkingHours wh = workingHoursRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), date.getDayOfWeek())
                .orElseThrow(() -> new ConflictException("Doctor not available on new date"));
        Instant newSlotEnd = newSlotStart.plusSeconds(wh.getSlotDurationMinutes() * 60L);
        if (!availabilityService.isValidSlot(doctor.getId(), date, newSlotStart, newSlotEnd)) {
            throw new ConflictException("New slot is not on valid grid");
        }
        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctor.getId(), date)) {
            throw new ConflictException("Doctor is on leave for new date");
        }
        // Check blocking appointment excluding current
        boolean blocked = appointmentRepository.existsByDoctorIdAndAppointmentStartAndStatusIn(doctor.getId(), newSlotStart, BLOCKING);
        if (blocked) {
            // Ensure it's not self (if self already at new slot, not blocked; but self is at old slot, so blocked means another appointment)
            Optional<Appointment> occupying = appointmentRepository.findByDoctorIdAndAppointmentStartAndStatusIn(doctor.getId(), newSlotStart, BLOCKING);
            if (occupying.isPresent() && !occupying.get().getId().equals(appt.getId())) {
                throw new ConflictException("New slot is already booked");
            } else if (occupying.isEmpty()) {
                throw new ConflictException("New slot is already booked");
            }
        }
        // Check active hold for new slot (non-expired)
        Optional<SlotHold> holdOpt = slotHoldRepository.findByDoctorIdAndSlotStartAndStatus(doctor.getId(), newSlotStart, SlotHoldStatus.ACTIVE);
        if (holdOpt.isPresent() && holdOpt.get().getExpiresAt().isAfter(now)) {
            throw new ConflictException("New slot is currently held");
        } else if (holdOpt.isPresent()) {
            holdOpt.get().setStatus(SlotHoldStatus.EXPIRED);
            slotHoldRepository.save(holdOpt.get());
        }

        // Attempt update – DB unique index protects concurrent reschedule/book
        Instant oldStart = appt.getAppointmentStart();
        try {
            appt.setAppointmentStart(newSlotStart);
            appt.setAppointmentEnd(newSlotEnd);
            appointmentRepository.saveAndFlush(appt);
        } catch (DataIntegrityViolationException ex) {
            // Restore old start in memory (will rollback anyway)
            throw new ConflictException("New slot was booked concurrently");
        }

        Notification notif = new Notification();
        notif.setUser(patient.getUser());
        notif.setAppointmentId(appt.getId());
        notif.setType(NotificationType.RESCHEDULE);
        notif.setStatus(NotificationStatus.PENDING);
        notif.setSubject("Appointment rescheduled");
        notif.setBody("Your appointment moved from "
                + EmailTemplateService.formatAppointmentTime(oldStart) + " to "
                + EmailTemplateService.formatAppointmentTime(newSlotStart));
        notificationRepository.save(notif);

        // Re-enqueue calendar syncs – worker updates existing Google events to the new time.
        calendarSyncService.enqueueForParticipants(appt);

        String sym = symptomRepository.findByAppointmentId(appt.getId()).map(SymptomSubmission::getSymptomText).orElse(null);
        return new AppointmentResponse(appt, sym);
    }

    private void enforceAccess(Appointment appt) {
        // Try patient
        try {
            Patient p = currentUserService.getCurrentPatient();
            if (appt.getPatient().getId().equals(p.getId())) return;
        } catch (Exception ignored) {}
        try {
            Doctor d = currentUserService.getCurrentDoctor();
            if (appt.getDoctor().getId().equals(d.getId())) return;
        } catch (Exception ignored) {}
        // Admin?
        var user = currentUserService.getCurrentUser();
        if (user.getRole().name().equals("ADMIN")) return;
        throw new AccessDeniedException("Access denied");
    }

    private void checkDoctorOrAdmin(Appointment appt) {
        try {
            Doctor d = currentUserService.getCurrentDoctor();
            if (appt.getDoctor().getId().equals(d.getId())) return;
        } catch (Exception ignored) {}
        var user = currentUserService.getCurrentUser();
        if (user.getRole().name().equals("ADMIN")) return;
        throw new AccessDeniedException("Access denied");
    }
}
