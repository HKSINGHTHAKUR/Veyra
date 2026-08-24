package com.healthcare.appointment.integration.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostVisitInput {
    private final String clinicalNotes;
    private final String followUpInstructions;
    private final List<PrescriptionInfo> prescriptions;

    @Getter
    @AllArgsConstructor
    public static class PrescriptionInfo {
        private final String medicationName;
        private final String dosage;
        private final String frequency;
        private final Integer durationDays;
        private final String instructions;
    }
}
