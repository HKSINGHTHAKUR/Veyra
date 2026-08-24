package com.healthcare.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class HoldRequest {
    @NotNull(message = "doctorId is required")
    private UUID doctorId;

    @NotNull(message = "slotStart is required")
    private Instant slotStart;
}
