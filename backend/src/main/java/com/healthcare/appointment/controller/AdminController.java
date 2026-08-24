package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.*;
import com.healthcare.appointment.service.DoctorLeaveService;
import com.healthcare.appointment.service.DoctorService;
import com.healthcare.appointment.service.WorkingHoursService;
import com.healthcare.appointment.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only portal endpoints.
 * Every method is protected with @PreAuthorize("hasRole('ADMIN')").
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final DoctorService doctorService;
    private final WorkingHoursService workingHoursService;
    private final DoctorLeaveService doctorLeaveService;

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(
                userRepository.findAll().stream().map(UserResponse::new).toList());
    }

    // ── Doctor management ─────────────────────────────────────────────────────

    @PostMapping("/doctors")
    public ResponseEntity<DoctorResponse> createDoctor(
            @Valid @RequestBody CreateDoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.createDoctor(request));
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorResponse>> listDoctors() {
        return ResponseEntity.ok(doctorService.getAllDoctors());
    }

    @GetMapping("/doctors/{doctorId}")
    public ResponseEntity<DoctorResponse> getDoctor(@PathVariable UUID doctorId) {
        return ResponseEntity.ok(doctorService.getDoctorById(doctorId));
    }

    @PatchMapping("/doctors/{doctorId}")
    public ResponseEntity<DoctorResponse> updateDoctor(
            @PathVariable UUID doctorId,
            @Valid @RequestBody UpdateDoctorRequest request) {
        return ResponseEntity.ok(doctorService.updateDoctor(doctorId, request));
    }

    @PatchMapping("/doctors/{doctorId}/deactivate")
    public ResponseEntity<DoctorResponse> deactivateDoctor(@PathVariable UUID doctorId) {
        return ResponseEntity.ok(doctorService.deactivateDoctor(doctorId));
    }

    @PatchMapping("/doctors/{doctorId}/activate")
    public ResponseEntity<DoctorResponse> activateDoctor(@PathVariable UUID doctorId) {
        return ResponseEntity.ok(doctorService.activateDoctor(doctorId));
    }

    // ── Working hours ─────────────────────────────────────────────────────────

    @PostMapping("/doctors/{doctorId}/working-hours")
    public ResponseEntity<WorkingHoursResponse> createWorkingHours(
            @PathVariable UUID doctorId,
            @Valid @RequestBody WorkingHoursRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workingHoursService.createWorkingHours(doctorId, request));
    }

    @PutMapping("/doctors/{doctorId}/working-hours/{workingHoursId}")
    public ResponseEntity<WorkingHoursResponse> updateWorkingHours(
            @PathVariable UUID doctorId,
            @PathVariable UUID workingHoursId,
            @Valid @RequestBody WorkingHoursRequest request) {
        return ResponseEntity.ok(
                workingHoursService.updateWorkingHours(doctorId, workingHoursId, request));
    }

    @GetMapping("/doctors/{doctorId}/working-hours")
    public ResponseEntity<List<WorkingHoursResponse>> getWorkingHours(
            @PathVariable UUID doctorId) {
        return ResponseEntity.ok(workingHoursService.getWorkingHours(doctorId));
    }

    @DeleteMapping("/doctors/{doctorId}/working-hours/{workingHoursId}")
    public ResponseEntity<Void> deleteWorkingHours(
            @PathVariable UUID doctorId,
            @PathVariable UUID workingHoursId) {
        workingHoursService.deleteWorkingHours(doctorId, workingHoursId);
        return ResponseEntity.noContent().build();
    }

    // ── Doctor leave ─────────────────────────────────────────────────────────

    @PostMapping("/doctors/{doctorId}/leaves")
    public ResponseEntity<LeaveResponse> addLeave(
            @PathVariable UUID doctorId,
            @Valid @RequestBody LeaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(doctorLeaveService.addLeave(doctorId, request));
    }

    @GetMapping("/doctors/{doctorId}/leaves")
    public ResponseEntity<List<LeaveResponse>> getLeaves(@PathVariable UUID doctorId) {
        return ResponseEntity.ok(doctorLeaveService.getLeaves(doctorId));
    }

    @DeleteMapping("/doctors/{doctorId}/leaves/{leaveId}")
    public ResponseEntity<Void> removeLeave(
            @PathVariable UUID doctorId,
            @PathVariable UUID leaveId) {
        doctorLeaveService.removeLeave(doctorId, leaveId);
        return ResponseEntity.noContent().build();
    }
}
