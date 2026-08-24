package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.AuthResponse;
import com.healthcare.appointment.dto.LoginRequest;
import com.healthcare.appointment.dto.RegisterRequest;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.Patient;
import com.healthcare.appointment.entity.Role;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.exception.EmailAlreadyExistsException;
import com.healthcare.appointment.repository.DoctorRepository;
import com.healthcare.appointment.repository.PatientRepository;
import com.healthcare.appointment.repository.UserRepository;
import com.healthcare.appointment.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a new patient account.
     * Public registration is restricted to the PATIENT role.
     * Creating ADMIN or DOCTOR accounts requires a privileged mechanism (Phase 3).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setRole(Role.PATIENT);
        user = userRepository.save(user);

        Patient patient = new Patient();
        patient.setUser(user);
        patient = patientRepository.save(patient);

        String token = jwtService.generateToken(user);
        return buildAuthResponse(token, user, patient.getId(), null);
    }

    /**
     * Authenticates a user by email and password.
     * The error message is intentionally generic — we do not reveal whether
     * the email exists or the password is wrong.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        UUID patientId = null;
        UUID doctorId = null;

        if (user.getRole() == Role.PATIENT) {
            patientId = patientRepository.findByUserId(user.getId())
                    .map(Patient::getId)
                    .orElse(null);
        } else if (user.getRole() == Role.DOCTOR) {
            doctorId = doctorRepository.findByUserId(user.getId())
                    .map(Doctor::getId)
                    .orElse(null);
        }

        String token = jwtService.generateToken(user);
        return buildAuthResponse(token, user, patientId, doctorId);
    }

    private AuthResponse buildAuthResponse(String token, User user,
                                           UUID patientId,
                                           UUID doctorId) {
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .patientId(patientId)
                .doctorId(doctorId)
                .build();
    }
}
