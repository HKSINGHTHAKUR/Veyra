package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.Doctor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full doctor representation for admin views.
 * Never exposes passwordHash or internal auth fields.
 */
@Getter
public class DoctorResponse {

    private final UUID id;
    private final UUID userId;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final String specialisation;
    private final String bio;
    private final BigDecimal consultationFee;
    private final boolean active;
    private final Instant createdAt;

    public DoctorResponse(Doctor doctor) {
        this.id              = doctor.getId();
        this.userId          = doctor.getUser().getId();
        this.email           = doctor.getUser().getEmail();
        this.firstName       = doctor.getUser().getFirstName();
        this.lastName        = doctor.getUser().getLastName();
        this.phone           = doctor.getUser().getPhone();
        this.specialisation  = doctor.getSpecialisation();
        this.bio             = doctor.getBio();
        this.consultationFee = doctor.getConsultationFee();
        this.active          = doctor.isActive();
        this.createdAt       = doctor.getCreatedAt();
    }
}
