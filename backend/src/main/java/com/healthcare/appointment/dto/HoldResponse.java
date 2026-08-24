package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.SlotHold;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class HoldResponse {
    private final UUID holdId;
    private final UUID doctorId;
    private final UUID patientId;
    private final Instant slotStart;
    private final Instant slotEnd;
    private final Instant expiresAt;
    private final String status;

    public HoldResponse(SlotHold hold) {
        this.holdId = hold.getId();
        this.doctorId = hold.getDoctor().getId();
        this.patientId = hold.getPatient().getId();
        this.slotStart = hold.getSlotStart();
        this.slotEnd = hold.getSlotEnd();
        this.expiresAt = hold.getExpiresAt();
        this.status = hold.getStatus().name();
    }
}
