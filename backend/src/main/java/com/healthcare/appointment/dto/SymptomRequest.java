package com.healthcare.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SymptomRequest {
    @NotBlank(message = "symptoms must not be blank")
    @Size(max = 5000, message = "symptoms too long (max 5000)")
    private String symptoms;
}
