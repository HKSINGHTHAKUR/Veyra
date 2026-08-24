package com.healthcare.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.healthcare.appointment.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Returned on successful registration or login.
 * Contains the JWT and the minimum user information the frontend needs
 * to route to the correct portal. Never contains password or hash.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String token;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;

    /** Non-null only for PATIENT role. */
    private UUID patientId;

    /** Non-null only for DOCTOR role. */
    private UUID doctorId;
}
