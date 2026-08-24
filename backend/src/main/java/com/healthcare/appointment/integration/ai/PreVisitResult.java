package com.healthcare.appointment.integration.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PreVisitResult {
    private final String urgency; // raw from LLM, validated against UrgencyLevel
    private final String chiefComplaint;
    private final List<String> suggestedQuestions;
}
