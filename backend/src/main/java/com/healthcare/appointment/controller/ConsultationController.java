package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.ConsultationResponse;
import com.healthcare.appointment.dto.CreateConsultationRequest;
import com.healthcare.appointment.service.ConsultationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @PostMapping("/api/appointments/{appointmentId}/consultation")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ConsultationResponse> createConsultation(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CreateConsultationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(consultationService.createConsultation(appointmentId, request));
    }

    @GetMapping("/api/appointments/{appointmentId}/consultation")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsultationResponse> getConsultation(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(consultationService.getConsultation(appointmentId));
    }
}
