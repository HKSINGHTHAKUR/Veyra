package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.SymptomSubmission;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class SymptomResponse {
    private final UUID id;
    private final UUID slotHoldId;
    private final UUID appointmentId;
    private final String symptomText;
    private final Instant submittedAt;

    public SymptomResponse(SymptomSubmission s) {
        this.id = s.getId();
        this.slotHoldId = s.getSlotHold() != null ? s.getSlotHold().getId() : null;
        this.appointmentId = s.getAppointment() != null ? s.getAppointment().getId() : null;
        this.symptomText = s.getSymptomText();
        this.submittedAt = s.getSubmittedAt();
    }
}
