package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.LeaveRequest;
import com.healthcare.appointment.dto.LeaveResponse;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.DoctorLeave;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.exception.ConflictException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.entity.Appointment;
import com.healthcare.appointment.entity.AppointmentStatus;
import com.healthcare.appointment.entity.NotificationType;
import com.healthcare.appointment.integration.email.EmailTemplateService;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.DoctorLeaveRepository;
import com.healthcare.appointment.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorLeaveService {

    private final DoctorRepository doctorRepository;
    private final DoctorLeaveRepository leaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final EmailTemplateService emailTemplateService;

    @Transactional
    public LeaveResponse addLeave(UUID doctorId, LeaveRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        if (!doctor.isActive()) {
            throw new BadRequestException("Cannot add leave for an inactive doctor");
        }

        if (leaveRepository.existsByDoctorIdAndLeaveDate(doctorId, request.getLeaveDate())) {
            throw new ConflictException(
                    "Leave already exists for doctor on " + request.getLeaveDate());
        }

        DoctorLeave leave = new DoctorLeave();
        leave.setDoctor(doctor);
        leave.setLeaveDate(request.getLeaveDate());
        leave.setReason(request.getReason());
        DoctorLeave saved = leaveRepository.save(leave);

        // Doctor-leave notification: find future appointments on that leave date
        // Do NOT delete appointments – just notify patients.
        // Past appointments are ignored; unaffected appointments not notified.
        LocalDate leaveDate = request.getLeaveDate();
        Instant dayStart = leaveDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = leaveDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = Instant.now();
        // Only future appointments should be notified
        List<Appointment> affected = appointmentRepository.findByDoctorId(doctorId).stream()
                .filter(a -> a.getAppointmentStart() != null
                        && !a.getAppointmentStart().isBefore(dayStart)
                        && a.getAppointmentStart().isBefore(dayEnd)
                        && a.getAppointmentStart().isAfter(now)
                        && (a.getStatus() == AppointmentStatus.BOOKED || a.getStatus() == AppointmentStatus.RESCHEDULED))
                .toList();

        for (Appointment appt : affected) {
            // Idempotency: avoid duplicate leave notifications for same appointment
            if (notificationService.existsDoctorLeaveForAppointment(appt.getId())) continue;
            String subject = emailTemplateService.doctorLeaveSubject(doctor, leaveDate);
            String body = emailTemplateService.doctorLeaveBody(appt, leaveDate);
            notificationService.createNotification(appt.getPatient().getUser(), appt.getId(),
                    NotificationType.DOCTOR_LEAVE, subject, body);
        }

        return new LeaveResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getLeaves(UUID doctorId) {
        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        return leaveRepository.findByDoctorId(doctorId)
                .stream()
                .map(LeaveResponse::new)
                .toList();
    }

    @Transactional
    public void removeLeave(UUID doctorId, UUID leaveId) {
        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        DoctorLeave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave", leaveId));

        if (!leave.getDoctor().getId().equals(doctorId)) {
            throw new ResourceNotFoundException("Leave", leaveId);
        }

        leaveRepository.delete(leave);
    }
}
