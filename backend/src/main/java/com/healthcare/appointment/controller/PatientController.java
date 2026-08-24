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
 * Patient portal endpoints.
 * All methods require PATIENT role — role is checked at the method level
 * so the authorization intent is explicit and not reliant solely on URL patterns.
 * Additional patient endpoints will be added in later phases.
 */
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(new UserResponse(currentUserService.getCurrentUser()));
    }
}
