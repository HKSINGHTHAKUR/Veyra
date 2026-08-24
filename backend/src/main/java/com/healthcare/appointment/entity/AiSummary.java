package com.healthcare.appointment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Stores the result of an AI-generated summary for an appointment.
 *
 * PRE_VISIT summaries populate: urgency, chiefComplaint, suggestedQuestions.
 * POST_VISIT summaries populate: summaryText.
 *
 * suggestedQuestions is stored as a JSON array string (e.g. ["Q1","Q2","Q3"]).
 * The LLM integration layer (Phase 5) is responsible for serialisation.
 *
 * The unique constraint on (appointment_id, type) means at most one summary
 * per type per appointment; a failed summary can be retried by updating the
 * existing row rather than inserting a duplicate.
 */
@Entity
@Table(name = "ai_summaries")
@Getter
@Setter
public class AiSummary extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AiSummaryType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiSummaryStatus status = AiSummaryStatus.PENDING;

    /** PRE_VISIT only. Never expose as a medical diagnosis. */
    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", length = 10)
    private UrgencyLevel urgency;

    /** PRE_VISIT only. */
    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    /**
     * PRE_VISIT only. JSON array of exactly three suggested questions.
     * Example: ["Question 1","Question 2","Question 3"]
     */
    @Column(name = "suggested_questions", columnDefinition = "TEXT")
    private String suggestedQuestions;

    /** POST_VISIT only. Patient-friendly narrative. */
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    /** POST_VISIT only. JSON array of medications for patient-friendly schedule. */
    @Column(name = "medications", columnDefinition = "TEXT")
    private String medications;

    /** POST_VISIT only. JSON array of follow-up steps. */
    @Column(name = "follow_up_steps", columnDefinition = "TEXT")
    private String followUpSteps;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
}
