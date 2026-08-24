package com.healthcare.appointment.service;

import com.healthcare.appointment.entity.Appointment;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.Patient;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.repository.DoctorRepository;
import com.healthcare.appointment.repository.PatientRepository;
import com.healthcare.appointment.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provides access to the currently authenticated user without scattering
 * SecurityContextHolder calls across controllers and services.
 *
 * Later phases use this to enforce ownership checks:
 * "Is this appointment owned by the current patient?"
 * "Is this consultation written by the current doctor?"
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public UserPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return principal;
    }

    public User getCurrentUser() {
        return getCurrentPrincipal().getUser();
    }

    public UUID getCurrentUserId() {
        return getCurrentPrincipal().getUserId();
    }

    @Transactional(readOnly = true, noRollbackFor = ResourceNotFoundException.class)
    public Patient getCurrentPatient() {
        UUID userId = getCurrentUserId();
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient profile not found for user: " + userId));
    }

    @Transactional(readOnly = true, noRollbackFor = ResourceNotFoundException.class)
    public Doctor getCurrentDoctor() {
        UUID userId = getCurrentUserId();
        return doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor profile not found for user: " + userId));
    }

    /**
     * Single reusable appointment access rule (consultations, AI summaries, etc.):
     * the owning PATIENT, the assigned DOCTOR, or an ADMIN may proceed;
     * anyone else gets AccessDeniedException -> HTTP 403.
     */
    public void enforceAppointmentAccess(Appointment appointment) {
        try {
            Patient p = getCurrentPatient();
            if (appointment.getPatient().getId().equals(p.getId())) return;
        } catch (Exception ignored) {}
        try {
            Doctor d = getCurrentDoctor();
            if (appointment.getDoctor().getId().equals(d.getId())) return;
        } catch (Exception ignored) {}
        User user = getCurrentUser();
        if ("ADMIN".equals(user.getRole().name())) return;
        throw new org.springframework.security.access.AccessDeniedException("Access denied");
    }
}
