package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.AiSummaryResponse;
import com.healthcare.appointment.service.AiSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AiSummaryController {

    private final AiSummaryService aiSummaryService;

    @GetMapping("/api/appointments/{appointmentId}/ai/pre-visit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiSummaryResponse> getPreVisit(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(aiSummaryService.getPreVisit(appointmentId));
    }

    @GetMapping("/api/appointments/{appointmentId}/ai/post-visit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiSummaryResponse> getPostVisit(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(aiSummaryService.getPostVisit(appointmentId));
    }

    // Manual trigger for testing/admin – not part of patient flow but useful for retry
    // Ownership enforced in service: pre-visit = owning PATIENT or ADMIN; post-visit = assigned DOCTOR or ADMIN
    @PostMapping("/api/appointments/{appointmentId}/ai/pre-visit/generate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PATIENT')")
    public ResponseEntity<AiSummaryResponse> generatePreVisit(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(aiSummaryService.generatePreVisitForCurrentUser(appointmentId));
    }

    @PostMapping("/api/appointments/{appointmentId}/ai/post-visit/generate")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('ADMIN')")
    public ResponseEntity<AiSummaryResponse> generatePostVisit(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(aiSummaryService.generatePostVisitForCurrentUser(appointmentId));
    }
}
