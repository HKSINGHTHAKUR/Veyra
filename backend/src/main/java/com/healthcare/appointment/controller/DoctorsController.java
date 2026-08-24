package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.AvailabilityResponse;
import com.healthcare.appointment.dto.DoctorSummaryResponse;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.service.AvailabilityService;
import com.healthcare.appointment.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public (authenticated) endpoints for doctor discovery and availability.
 * Accessible to any authenticated user — patients use these to find doctors
 * and view available appointment slots.
 */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorsController {

    private final DoctorService doctorService;
    private final AvailabilityService availabilityService;

    /**
     * Lists active doctors, optionally filtered by specialisation.
     * Example: GET /api/doctors?specialisation=Cardiology
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DoctorSummaryResponse>> searchDoctors(
            @RequestParam(required = false) String specialisation) {
        return ResponseEntity.ok(doctorService.searchActiveDoctors(specialisation));
    }

    @GetMapping("/{doctorId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DoctorSummaryResponse> getDoctor(@PathVariable UUID doctorId) {
        return ResponseEntity.ok(doctorService.getDoctorSummary(doctorId));
    }

    /**
     * Returns a doctor's availability for a specific date.
     * Example: GET /api/doctors/{id}/availability?date=2026-08-25
     *
     * Slots are in UTC. Only non-past slots are included.
     * Slots with available=false are occupied (booked or held).
     */
    @GetMapping("/{doctorId}/availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            throw new BadRequestException("date parameter is required (format: YYYY-MM-DD)");
        }
        return ResponseEntity.ok(availabilityService.getAvailability(doctorId, date));
    }
}
