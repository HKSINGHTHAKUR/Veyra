package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.UserResponse;
import com.healthcare.appointment.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Doctor portal endpoints.
 * All methods require DOCTOR role.
 * Additional doctor endpoints will be added in Phase 3.
 */
@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(new UserResponse(currentUserService.getCurrentUser()));
    }
}
