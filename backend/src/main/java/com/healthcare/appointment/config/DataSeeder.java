package com.healthcare.appointment.config;

import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.Patient;
import com.healthcare.appointment.entity.Role;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.repository.DoctorRepository;
import com.healthcare.appointment.repository.PatientRepository;
import com.healthcare.appointment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds development accounts on first startup.
 * Only runs outside the "test" profile AND only when explicitly enabled via
 * configuration (app.seed.enabled=true / APP_SEED_ENABLED=true). Default is OFF
 * so no known credentials ever exist in a real deployment.
 *
 * Development credentials (local environments only — change before sharing):
 *   admin@healthcare.dev   / Admin@Dev1
 *   doctor@healthcare.dev  / Doctor@Dev1
 *   patient@healthcare.dev / Patient@Dev1
 *
 * Idempotent: checks for existence before creating.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataSeeder {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.seed.enabled:false}")
    private boolean seedEnabled;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!seedEnabled) {
            log.info("DEV SEED: disabled (set app.seed.enabled=true to create development accounts)");
            return;
        }
        seedAdmin();
        seedDoctor();
        seedPatient();
    }

    private void seedAdmin() {
        String email = "admin@healthcare.dev";
        if (userRepository.existsByEmail(email)) return;

        User admin = buildUser(email, "Admin@Dev1", "Admin", "User", Role.ADMIN);
        userRepository.save(admin);
        log.info("DEV SEED: Created admin account — {}", email);
    }

    private void seedDoctor() {
        String email = "doctor@healthcare.dev";
        if (userRepository.existsByEmail(email)) return;

        User user = buildUser(email, "Doctor@Dev1", "Dev", "Doctor", Role.DOCTOR);
        user = userRepository.save(user);

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialisation("General Practice");
        doctor.setBio("Development seed account — not a real doctor.");
        doctorRepository.save(doctor);
        log.info("DEV SEED: Created doctor account — {}", email);
    }

    private void seedPatient() {
        String email = "patient@healthcare.dev";
        if (userRepository.existsByEmail(email)) return;

        User user = buildUser(email, "Patient@Dev1", "Dev", "Patient", Role.PATIENT);
        user = userRepository.save(user);

        Patient patient = new Patient();
        patient.setUser(user);
        patientRepository.save(patient);
        log.info("DEV SEED: Created patient account — {}", email);
    }

    private User buildUser(String email, String rawPassword, String firstName,
                           String lastName, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        return user;
    }
}
