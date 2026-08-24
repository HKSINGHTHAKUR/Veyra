package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.AiSummaryResponse;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.integration.ai.*;
import com.healthcare.appointment.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private final AiSummaryRepository aiSummaryRepository;
    private final AppointmentRepository appointmentRepository;
    private final SymptomSubmissionRepository symptomRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final CurrentUserService currentUserService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String modelName;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Authorized manual entry point (HTTP): only the owning PATIENT or an ADMIN
     * may trigger pre-visit generation. Doctors cannot. The unauthenticated
     * {@link #generatePreVisit(UUID)} remains for the AFTER_COMMIT event listener.
     */
    public AiSummaryResponse generatePreVisitForCurrentUser(UUID appointmentId) {
        User user = currentUserService.getCurrentUser();
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        boolean admin = "ADMIN".equals(user.getRole().name());
        boolean owningPatient = appt.getPatient().getUser().getId().equals(user.getId());
        if (!admin && !owningPatient) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return generatePreVisit(appointmentId);
    }

    /**
     * Authorized manual entry point (HTTP): only the assigned DOCTOR or an ADMIN
     * may trigger post-visit generation. Patients cannot.
     */
    public AiSummaryResponse generatePostVisitForCurrentUser(UUID appointmentId) {
        User user = currentUserService.getCurrentUser();
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        boolean admin = "ADMIN".equals(user.getRole().name());
        boolean assignedDoctor = appt.getDoctor().getUser().getId().equals(user.getId());
        if (!admin && !assignedDoctor) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return generatePostVisit(appointmentId);
    }

    @Transactional
    public AiSummaryResponse generatePreVisit(UUID appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        // Duplicate prevention
        var existing = aiSummaryRepository.findByAppointmentIdAndType(appointmentId, AiSummaryType.PRE_VISIT);
        if (existing.isPresent()) {
            AiSummary ex = existing.get();
            if (ex.getStatus() == AiSummaryStatus.COMPLETED) return toResponse(ex);
            if (ex.getStatus() == AiSummaryStatus.PENDING) return toResponse(ex);
            // FAILED → allow regeneration by reusing row
            return regeneratePreVisit(ex, appt);
        }

        AiSummary summary = new AiSummary();
        summary.setAppointment(appt);
        summary.setType(AiSummaryType.PRE_VISIT);
        summary.setStatus(AiSummaryStatus.PENDING);
        summary.setModelUsed(modelName);
        summary = aiSummaryRepository.save(summary);

        return generatePreVisitInternal(summary, appt);
    }

    @Transactional
    public AiSummaryResponse generatePostVisit(UUID appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        var existing = aiSummaryRepository.findByAppointmentIdAndType(appointmentId, AiSummaryType.POST_VISIT);
        if (existing.isPresent()) {
            AiSummary ex = existing.get();
            if (ex.getStatus() == AiSummaryStatus.COMPLETED) return toResponse(ex);
            if (ex.getStatus() == AiSummaryStatus.PENDING) return toResponse(ex);
            return regeneratePostVisit(ex, appt);
        }

        AiSummary summary = new AiSummary();
        summary.setAppointment(appt);
        summary.setType(AiSummaryType.POST_VISIT);
        summary.setStatus(AiSummaryStatus.PENDING);
        summary.setModelUsed(modelName);
        summary = aiSummaryRepository.save(summary);

        return generatePostVisitInternal(summary, appt);
    }

    @Transactional(readOnly = true, noRollbackFor = org.springframework.security.access.AccessDeniedException.class)
    public AiSummaryResponse getPreVisit(UUID appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        enforceAccess(appt);
        AiSummary s = aiSummaryRepository.findByAppointmentIdAndType(appointmentId, AiSummaryType.PRE_VISIT)
                .orElseThrow(() -> new ResourceNotFoundException("AiSummary", appointmentId));
        return toResponse(s);
    }

    @Transactional(readOnly = true, noRollbackFor = org.springframework.security.access.AccessDeniedException.class)
    public AiSummaryResponse getPostVisit(UUID appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        enforceAccess(appt);
        AiSummary s = aiSummaryRepository.findByAppointmentIdAndType(appointmentId, AiSummaryType.POST_VISIT)
                .orElseThrow(() -> new ResourceNotFoundException("AiSummary", appointmentId));
        return toResponse(s);
    }

    // ── Internal generation ──────────────────────────────────────────────

    private AiSummaryResponse regeneratePreVisit(AiSummary summary, Appointment appt) {
        summary.setStatus(AiSummaryStatus.PENDING);
        summary.setFailureReason(null);
        aiSummaryRepository.save(summary);
        return generatePreVisitInternal(summary, appt);
    }

    private AiSummaryResponse regeneratePostVisit(AiSummary summary, Appointment appt) {
        summary.setStatus(AiSummaryStatus.PENDING);
        summary.setFailureReason(null);
        aiSummaryRepository.save(summary);
        return generatePostVisitInternal(summary, appt);
    }

    private AiSummaryResponse generatePreVisitInternal(AiSummary summary, Appointment appt) {
        try {
            // Input: symptoms + doctor specialisation
            String symptoms = symptomRepository.findByAppointmentId(appt.getId())
                    .map(SymptomSubmission::getSymptomText)
                    .orElse(symptomRepository.findBySlotHoldId(appt.getSlotHoldId())
                            .map(SymptomSubmission::getSymptomText).orElse(""));
            if (symptoms == null) symptoms = "";
            String spec = appt.getDoctor().getSpecialisation();

            PreVisitInput input = new PreVisitInput(symptoms, spec, appt.getId().toString());
            PreVisitResult result = llmClient.generatePreVisit(input);

            // Validation
            UrgencyLevel urgency = null;
            try {
                urgency = UrgencyLevel.valueOf(result.getUrgency());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid urgency: " + result.getUrgency());
            }
            if (result.getChiefComplaint() == null || result.getChiefComplaint().isBlank()) {
                throw new IllegalArgumentException("Missing chief complaint");
            }
            if (result.getSuggestedQuestions() == null || result.getSuggestedQuestions().size() != 3) {
                throw new IllegalArgumentException("Exactly 3 questions required, got " + (result.getSuggestedQuestions()==null?0:result.getSuggestedQuestions().size()));
            }
            for (String q : result.getSuggestedQuestions()) {
                if (q == null || q.isBlank()) throw new IllegalArgumentException("Empty question");
            }

            summary.setUrgency(urgency);
            summary.setChiefComplaint(result.getChiefComplaint().strip());
            summary.setSuggestedQuestions(objectMapper.writeValueAsString(result.getSuggestedQuestions()));
            summary.setStatus(AiSummaryStatus.COMPLETED);
            summary.setFailureReason(null);
            summary = aiSummaryRepository.save(summary);
            return toResponse(summary);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // Do not expose stack trace or raw LLM internals; log server-side without sensitive clinical data
            log.warn("Pre-visit generation failed for appointment {}: {}", appt.getId(), msg);
            summary.setStatus(AiSummaryStatus.FAILED);
            summary.setFailureReason(truncate(msg));
            aiSummaryRepository.save(summary);
            return toResponse(summary);
        }
    }

    private AiSummaryResponse generatePostVisitInternal(AiSummary summary, Appointment appt) {
        try {
            Consultation consultation = consultationRepository.findByAppointmentId(appt.getId())
                    .orElseThrow(() -> new IllegalStateException("Consultation not found"));
            List<PrescriptionItem> prescriptions = prescriptionItemRepository.findByConsultationId(consultation.getId());

            PostVisitInput input = new PostVisitInput(
                    consultation.getClinicalNotes(),
                    consultation.getFollowUpInstructions(),
                    prescriptions.stream().map(p -> new PostVisitInput.PrescriptionInfo(
                            p.getMedicationName(), p.getDosage(), p.getFrequency(), p.getDurationDays(), p.getInstructions())).toList()
            );

            PostVisitResult result = llmClient.generatePostVisit(input);

            if (result.getSummary() == null || result.getSummary().isBlank()) {
                throw new IllegalArgumentException("Missing summary");
            }
            if (result.getFollowUpSteps() == null || result.getFollowUpSteps().isEmpty()) {
                throw new IllegalArgumentException("Missing follow-up steps");
            }
            for (String s : result.getFollowUpSteps()) if (s==null||s.isBlank()) throw new IllegalArgumentException("Empty follow-up step");

            // Validate medications: must match DB exactly (no fabrication)
            List<PostVisitInput.PrescriptionInfo> dbMeds = input.getPrescriptions();
            List<PostVisitResult.Medication> llmMeds = result.getMedications() != null ? result.getMedications() : List.of();
            if (dbMeds.isEmpty() && !llmMeds.isEmpty()) {
                throw new IllegalArgumentException("LLM invented medications when none prescribed");
            }
            if (!dbMeds.isEmpty() && llmMeds.size() != dbMeds.size()) {
                throw new IllegalArgumentException("Medication count mismatch: expected " + dbMeds.size() + " got " + llmMeds.size());
            }
            // If LLM meds present, prefer DB data for persistence to avoid fabrication
            String medsJson;
            String followUpJson;
            try {
                // Persist medications from DB (authoritative), not LLM, to guarantee correctness
                List<AiSummaryResponse.MedicationDto> medDtos = dbMeds.stream()
                        .map(p -> new AiSummaryResponse.MedicationDto(
                                p.getMedicationName(), p.getDosage(), p.getFrequency(),
                                p.getDurationDays() != null ? p.getDurationDays() + " days" : ""))
                        .toList();
                medsJson = objectMapper.writeValueAsString(medDtos);
                followUpJson = objectMapper.writeValueAsString(result.getFollowUpSteps());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize post-visit data");
            }

            summary.setSummaryText(result.getSummary().strip());
            summary.setMedications(medsJson);
            summary.setFollowUpSteps(followUpJson);
            summary.setStatus(AiSummaryStatus.COMPLETED);
            summary.setFailureReason(null);
            summary = aiSummaryRepository.save(summary);
            return toResponse(summary);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Post-visit generation failed for appointment {}: {}", appt.getId(), msg);
            summary.setStatus(AiSummaryStatus.FAILED);
            summary.setFailureReason(truncate(msg));
            aiSummaryRepository.save(summary);
            return toResponse(summary);
        }
    }

    private AiSummaryResponse toResponse(AiSummary s) {
        return new AiSummaryResponse(s, objectMapper);
    }

    private void enforceAccess(Appointment appt) {
        currentUserService.enforceAppointmentAccess(appt);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
