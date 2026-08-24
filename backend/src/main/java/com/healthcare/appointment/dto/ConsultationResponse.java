package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.Consultation;
import com.healthcare.appointment.entity.PrescriptionItem;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class ConsultationResponse {
    private final UUID id;
    private final UUID appointmentId;
    private final UUID doctorId;
    private final String clinicalNotes;
    private final String followUpInstructions;
    private final Instant consultationTimestamp;
    private final List<PrescriptionDto> prescriptions;

    public ConsultationResponse(Consultation c, List<PrescriptionItem> items) {
        this.id = c.getId();
        this.appointmentId = c.getAppointment().getId();
        this.doctorId = c.getDoctor().getId();
        this.clinicalNotes = c.getClinicalNotes();
        this.followUpInstructions = c.getFollowUpInstructions();
        this.consultationTimestamp = c.getConsultationTimestamp();
        this.prescriptions = items.stream().map(p -> new PrescriptionDto(
                p.getId(), p.getMedicationName(), p.getDosage(), p.getFrequency(), p.getDurationDays(), p.getInstructions()
        )).toList();
    }

    public record PrescriptionDto(UUID id, String medicationName, String dosage, String frequency, Integer durationDays, String instructions) {}
}
