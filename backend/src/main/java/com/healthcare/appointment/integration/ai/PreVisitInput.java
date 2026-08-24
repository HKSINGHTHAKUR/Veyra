package com.healthcare.appointment.integration.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreVisitInput {
    private final String symptoms;
    private final String doctorSpecialisation;
    private final String appointmentId; // for logging only, not sent as sensitive
}
