package com.healthcare.appointment.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelRequest {
    @Size(max = 500, message = "reason too long")
    private String reason;
}
