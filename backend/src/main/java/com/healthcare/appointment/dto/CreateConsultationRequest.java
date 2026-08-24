package com.healthcare.appointment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateConsultationRequest {

    @NotBlank(message = "Clinical notes are required")
    private String clinicalNotes;

    private String followUpInstructions;

    @Valid
    private List<PrescriptionRequest> prescriptions;

    @Getter
    @Setter
    public static class PrescriptionRequest {
        @NotBlank
        @Size(max = 255)
        private String medicationName;

        @NotBlank
        @Size(max = 100)
        private String dosage;

        @NotBlank
        @Size(max = 100)
        private String frequency;

        private Integer durationDays;

        @Size(max = 1000)
        private String instructions;
    }
}
