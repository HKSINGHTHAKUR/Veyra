package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.SymptomResponse;
import com.healthcare.appointment.entity.Patient;
import com.healthcare.appointment.entity.SlotHold;
import com.healthcare.appointment.entity.SlotHoldStatus;
import com.healthcare.appointment.entity.SymptomSubmission;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.exception.ConflictException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.repository.SlotHoldRepository;
import com.healthcare.appointment.repository.SymptomSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SymptomService {

    private final SlotHoldRepository slotHoldRepository;
    private final SymptomSubmissionRepository symptomRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public SymptomResponse submitSymptoms(UUID holdId, String symptomText) {
        if (symptomText == null || symptomText.isBlank()) {
            throw new BadRequestException("symptoms must not be blank");
        }
        if (symptomText.length() > 5000) {
            throw new BadRequestException("symptoms too long");
        }

        Patient patient = currentUserService.getCurrentPatient();
        SlotHold hold = slotHoldRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold", holdId));

        if (!hold.getPatient().getId().equals(patient.getId())) {
            throw new ResourceNotFoundException("Hold", holdId);
        }
        if (hold.getStatus() != SlotHoldStatus.ACTIVE) {
            throw new ConflictException("Hold is not active");
        }
        if (hold.getExpiresAt().isBefore(Instant.now())) {
            hold.setStatus(SlotHoldStatus.EXPIRED);
            slotHoldRepository.save(hold);
            throw new ConflictException("Hold has expired");
        }

        // Upsert: if symptom for hold already exists, update
        SymptomSubmission symptom = symptomRepository.findBySlotHoldId(holdId)
                .orElseGet(() -> {
                    SymptomSubmission s = new SymptomSubmission();
                    s.setSlotHold(hold);
                    s.setPatient(patient);
                    return s;
                });
        symptom.setSymptomText(symptomText.strip());
        // keep appointment null until confirmation
        symptom = symptomRepository.save(symptom);

        return new SymptomResponse(symptom);
    }
}
