package com.healthcare.appointment.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Admin request to update a doctor's profile.
 * All fields are optional; only non-null fields are applied.
 */
@Getter
@Setter
public class UpdateDoctorRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String phone;

    @Size(max = 150)
    private String specialisation;

    @Size(max = 2000)
    private String bio;

    private BigDecimal consultationFee;
}
