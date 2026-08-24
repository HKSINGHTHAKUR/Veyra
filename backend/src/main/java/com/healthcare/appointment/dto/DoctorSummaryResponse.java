package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.Doctor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Patient-visible doctor information.
 * Excludes email, userId and any internal authentication fields.
 */
@Getter
public class DoctorSummaryResponse {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final String specialisation;
    private final String bio;
    private final BigDecimal consultationFee;

    public DoctorSummaryResponse(Doctor doctor) {
        this.id              = doctor.getId();
        this.firstName       = doctor.getUser().getFirstName();
        this.lastName        = doctor.getUser().getLastName();
        this.specialisation  = doctor.getSpecialisation();
        this.bio             = doctor.getBio();
        this.consultationFee = doctor.getConsultationFee();
    }
}
