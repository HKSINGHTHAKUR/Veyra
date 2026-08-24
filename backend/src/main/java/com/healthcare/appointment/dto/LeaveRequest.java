package com.healthcare.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class LeaveRequest {

    @NotNull(message = "Leave date is required")
    private LocalDate leaveDate;

    private String reason;
}
