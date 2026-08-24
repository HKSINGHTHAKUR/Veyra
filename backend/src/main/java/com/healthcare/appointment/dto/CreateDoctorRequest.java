package com.healthcare.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Admin request to create a new doctor account.
 * The temporary password must be communicated to the doctor out-of-band.
 * Public registration for DOCTOR role is not permitted.
 */
@Getter
@Setter
public class CreateDoctorRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    @NotBlank(message = "Specialisation is required")
    @Size(max = 150)
    private String specialisation;

    @Size(max = 2000)
    private String bio;

    private BigDecimal consultationFee;

    /** Initial password — must be changed by the doctor on first login. */
    @NotBlank(message = "Temporary password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String temporaryPassword;
}
