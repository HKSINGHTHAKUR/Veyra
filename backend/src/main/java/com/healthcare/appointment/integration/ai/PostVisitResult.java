package com.healthcare.appointment.integration.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostVisitResult {
    private final String summary;
    private final List<Medication> medications;
    private final List<String> followUpSteps;

    @Getter
    @AllArgsConstructor
    public static class Medication {
        private final String name;
        private final String dosage;
        private final String frequency;
        private final String duration;
    }
}
