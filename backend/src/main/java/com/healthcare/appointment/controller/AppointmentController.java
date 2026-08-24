package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.*;
import com.healthcare.appointment.service.AppointmentService;
import com.healthcare.appointment.service.SlotHoldService;
import com.healthcare.appointment.service.SymptomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Booking engine controller – holds, symptoms, confirmation, appointments,
 * cancellation and rescheduling.
 */
@RestController
@RequiredArgsConstructor
public class AppointmentController {

    private final SlotHoldService slotHoldService;
    private final SymptomService symptomService;
    private final AppointmentService appointmentService;

    // ── Hold creation (patient only) ──────────────────────────────────────
    @PostMapping("/api/appointments/hold")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<HoldResponse> createHold(@Valid @RequestBody HoldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(slotHoldService.createHold(request.getDoctorId(), request.getSlotStart()));
    }

    @GetMapping("/api/appointments/holds/{holdId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HoldResponse> getHold(@PathVariable UUID holdId) {
        return ResponseEntity.ok(slotHoldService.getHold(holdId));
    }

    // ── Symptoms ──────────────────────────────────────────────────────────
    @PostMapping("/api/appointments/{holdId}/symptoms")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<SymptomResponse> submitSymptoms(
            @PathVariable UUID holdId,
            @Valid @RequestBody SymptomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(symptomService.submitSymptoms(holdId, request.getSymptoms()));
    }

    // ── Confirmation ──────────────────────────────────────────────────────
    @PostMapping("/api/appointments/{holdId}/confirm")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> confirm(@PathVariable UUID holdId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.confirmHold(holdId));
    }

    // ── Appointment details ───────────────────────────────────────────────
    @GetMapping("/api/appointments/{appointmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AppointmentResponse> getAppointment(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(appointmentService.getAppointment(appointmentId));
    }

    @PostMapping("/api/appointments/{appointmentId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable UUID appointmentId,
            @RequestBody(required = false) CancelRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(appointmentService.cancelAppointment(appointmentId, reason));
    }

    @PostMapping("/api/appointments/{appointmentId}/reschedule")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(appointmentId, request.getNewSlotStart()));
    }

    // ── Patient appointments ──────────────────────────────────────────────
    @GetMapping("/api/patient/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentResponse>> patientAppointments() {
        return ResponseEntity.ok(appointmentService.getPatientAppointments());
    }

    // ── Doctor appointments ───────────────────────────────────────────────
    @GetMapping("/api/doctor/appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentResponse>> doctorAppointments() {
        return ResponseEntity.ok(appointmentService.getDoctorAppointments());
    }
}
