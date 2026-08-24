package com.healthcare.appointment.integration.ai;

/**
 * Prompt templates versioned as PRE_VISIT_V1 / POST_VISIT_V1.
 * Stored as Java constants for maintainability; could be externalized to resources/prompts/.
 */
public final class AiPromptTemplates {

    private AiPromptTemplates() {}

    public static final String PRE_VISIT_VERSION = "PRE_VISIT_V1";
    public static final String POST_VISIT_VERSION = "POST_VISIT_V1";

    public static String preVisitPrompt(PreVisitInput input) {
        return """
                You are a clinical assistant providing pre-visit summaries. Do not diagnose.
                Summarize the patient's symptoms, identify urgency using ONLY [LOW, MEDIUM, HIGH],
                identify chief complaint, and produce exactly 3 suggested questions for the doctor.
                Do not invent facts. Return structured JSON only.

                Symptoms: %s
                Doctor specialisation: %s

                Required JSON format:
                {
                  "urgency": "MEDIUM",
                  "chiefComplaint": "string",
                  "suggestedQuestions": ["Q1", "Q2", "Q3"]
                }
                urgency must be one of LOW, MEDIUM, HIGH.
                chiefComplaint must be non-empty.
                suggestedQuestions must be exactly 3 non-empty strings.
                """.formatted(
                sanitize(input.getSymptoms()),
                sanitize(input.getDoctorSpecialisation())
        );
    }

    public static String postVisitPrompt(PostVisitInput input) {
        StringBuilder rx = new StringBuilder();
        if (input.getPrescriptions() == null || input.getPrescriptions().isEmpty()) {
            rx.append("No prescriptions.");
        } else {
            for (PostVisitInput.PrescriptionInfo p : input.getPrescriptions()) {
                rx.append("- ").append(p.getMedicationName())
                  .append(" / ").append(p.getDosage())
                  .append(" / ").append(p.getFrequency());
                if (p.getDurationDays() != null) rx.append(" / ").append(p.getDurationDays()).append(" days");
                if (p.getInstructions() != null) rx.append(" / ").append(p.getInstructions());
                rx.append("\n");
            }
        }
        return """
                You are a clinical assistant creating patient-friendly post-visit summaries.
                Use ONLY the doctor's clinical notes and prescription data provided. Do not invent medications.
                If no prescriptions, return empty medications array. Do not invent facts.

                Clinical notes: %s
                Follow-up instructions: %s
                Prescriptions:
                %s

                Required JSON format:
                {
                  "summary": "patient-friendly narrative",
                  "medications": [
                    {"name": "string", "dosage": "string", "frequency": "string", "duration": "string"}
                  ],
                  "followUpSteps": ["step1", "step2"]
                }
                medications must match prescription data exactly; if none, empty array.
                followUpSteps must be non-empty array.
                summary must be non-empty.
                """.formatted(
                sanitize(input.getClinicalNotes()),
                sanitize(input.getFollowUpInstructions() != null ? input.getFollowUpInstructions() : ""),
                rx.toString()
        );
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("\n", " ").trim();
    }
}
