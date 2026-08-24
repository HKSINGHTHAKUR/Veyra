package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.ConsultationResponse;
import com.healthcare.appointment.dto.CreateConsultationRequest;
import com.healthcare.appointment.entity.Appointment;
import com.healthcare.appointment.entity.Consultation;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.NotificationType;
import com.healthcare.appointment.entity.PrescriptionItem;
import com.healthcare.appointment.event.ConsultationCompletedEvent;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.ConsultationRepository;
import com.healthcare.appointment.repository.NotificationRepository;
import com.healthcare.appointment.repository.PrescriptionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConsultationResponse createConsultation(UUID appointmentId, CreateConsultationRequest request) {
        Doctor doctor = currentUserService.getCurrentDoctor();
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        if (!appt.getDoctor().getId().equals(doctor.getId())) {
            throw new ResourceNotFoundException("Appointment", appointmentId);
        }
        if (consultationRepository.existsByAppointmentId(appointmentId)) {
            throw new BadRequestException("Consultation already exists for this appointment");
        }

        Consultation consultation = new Consultation();
        consultation.setAppointment(appt);
        consultation.setDoctor(doctor);
        consultation.setClinicalNotes(request.getClinicalNotes());
        consultation.setFollowUpInstructions(request.getFollowUpInstructions());
        consultation.setConsultationTimestamp(Instant.now());
        consultation = consultationRepository.save(consultation);

        if (request.getPrescriptions() != null) {
            for (CreateConsultationRequest.PrescriptionRequest pr : request.getPrescriptions()) {
                PrescriptionItem item = new PrescriptionItem();
                item.setConsultation(consultation);
                item.setMedicationName(pr.getMedicationName());
                item.setDosage(pr.getDosage());
                item.setFrequency(pr.getFrequency());
                item.setDurationDays(pr.getDurationDays());
                item.setInstructions(pr.getInstructions());
                prescriptionItemRepository.save(item);
            }
        }

        List<PrescriptionItem> items = prescriptionItemRepository.findByConsultationId(consultation.getId());

        // Minimal MEDICATION_REMINDER (assignment requirement): when a prescription is
        // issued, enqueue one PENDING notification listing the medications. The existing
        // background email worker delivers it; no separate reminder engine.
        if (!items.isEmpty() && !notificationRepository.existsByAppointmentIdAndType(
                appointmentId, NotificationType.MEDICATION_REMINDER)) {
            var notification = new com.healthcare.appointment.entity.Notification();
            notification.setUser(appt.getPatient().getUser());
            notification.setAppointmentId(appointmentId);
            notification.setType(NotificationType.MEDICATION_REMINDER);
            notification.setStatus(com.healthcare.appointment.entity.NotificationStatus.PENDING);
            notification.setSubject("Your prescriptions");
            notification.setBody("Medications prescribed: "
                    + items.stream().map(PrescriptionItem::getMedicationName)
                        .collect(Collectors.joining(", "))
                    + ". Please take them exactly as directed by your doctor.");
            notificationRepository.save(notification);
        }

        // Publish post-visit AI event AFTER_COMMIT – failure must not rollback consultation
        eventPublisher.publishEvent(new ConsultationCompletedEvent(this, appointmentId));

        return new ConsultationResponse(consultation, items);
    }

    @Transactional(readOnly = true, noRollbackFor = org.springframework.security.access.AccessDeniedException.class)
    public ConsultationResponse getConsultation(UUID appointmentId) {
        Consultation c = consultationRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", appointmentId));
        // Owning patient / assigned doctor / ADMIN only — anyone else gets 403
        currentUserService.enforceAppointmentAccess(c.getAppointment());
        List<PrescriptionItem> items = prescriptionItemRepository.findByConsultationId(c.getId());
        return new ConsultationResponse(c, items);
    }
}
