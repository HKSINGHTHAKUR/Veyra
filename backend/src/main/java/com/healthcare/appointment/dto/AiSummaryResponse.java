package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.AiSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class AiSummaryResponse {
    private final UUID id;
    private final UUID appointmentId;
    private final String type;
    private final String status;
    private final String urgency;
    private final String chiefComplaint;
    private final List<String> suggestedQuestions;
    private final String summaryText;
    private final List<MedicationDto> medications;
    private final List<String> followUpSteps;
    private final String modelUsed;
    private final String failureReason;
    private final Instant createdAt;

    public AiSummaryResponse(AiSummary s, ObjectMapper mapper) {
        this.id = s.getId();
        this.appointmentId = s.getAppointment().getId();
        this.type = s.getType().name();
        this.status = s.getStatus().name();
        this.urgency = s.getUrgency() != null ? s.getUrgency().name() : null;
        this.chiefComplaint = s.getChiefComplaint();
        this.suggestedQuestions = parseQuestions(s.getSuggestedQuestions(), mapper);
        this.summaryText = s.getSummaryText();
        this.medications = parseMedications(s.getMedications(), mapper);
        this.followUpSteps = parseSteps(s.getFollowUpSteps(), mapper);
        this.modelUsed = s.getModelUsed();
        this.failureReason = s.getFailureReason();
        this.createdAt = s.getCreatedAt();
    }

    private List<String> parseQuestions(String json, ObjectMapper mapper) {
        if (json == null) return null;
        try { return mapper.readValue(json, new TypeReference<List<String>>(){}); } catch(Exception e){return null;}
    }
    private List<MedicationDto> parseMedications(String json, ObjectMapper mapper) {
        if (json == null) return null;
        try { return mapper.readValue(json, new TypeReference<List<MedicationDto>>(){}); } catch(Exception e){return null;}
    }
    private List<String> parseSteps(String json, ObjectMapper mapper) {
        if (json == null) return null;
        try { return mapper.readValue(json, new TypeReference<List<String>>(){}); } catch(Exception e){return null;}
    }

    public record MedicationDto(String name, String dosage, String frequency, String duration) {}
}
