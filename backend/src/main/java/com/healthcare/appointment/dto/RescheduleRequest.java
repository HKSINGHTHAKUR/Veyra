package com.healthcare.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class RescheduleRequest {
    @NotNull(message = "newSlotStart is required")
    private Instant newSlotStart;
}
