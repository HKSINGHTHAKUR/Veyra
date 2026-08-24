package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.Appointment;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class AppointmentResponse {
    private final UUID id;
    private final UUID patientId;
    private final UUID doctorId;
    private final String doctorSpecialisation;
    private final String patientEmail;
    private final String doctorEmail;
    private final Instant appointmentStart;
    private final Instant appointmentEnd;
    private final String status;
    private final Instant bookedAt;
    private final UUID slotHoldId;
    private final String symptomText;

    public AppointmentResponse(Appointment a, String symptomText) {
        this.id = a.getId();
        this.patientId = a.getPatient().getId();
        this.doctorId = a.getDoctor().getId();
        this.doctorSpecialisation = a.getDoctor().getSpecialisation();
        this.patientEmail = a.getPatient().getUser().getEmail();
        this.doctorEmail = a.getDoctor().getUser().getEmail();
        this.appointmentStart = a.getAppointmentStart();
        this.appointmentEnd = a.getAppointmentEnd();
        this.status = a.getStatus().name();
        this.bookedAt = a.getBookedAt();
        this.slotHoldId = a.getSlotHoldId();
        this.symptomText = symptomText;
    }

    public AppointmentResponse(Appointment a) {
        this(a, null);
    }
}
