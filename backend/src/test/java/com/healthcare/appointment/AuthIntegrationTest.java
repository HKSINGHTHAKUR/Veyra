package com.healthcare.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.dto.LoginRequest;
import com.healthcare.appointment.dto.RegisterRequest;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.Patient;
import com.healthcare.appointment.entity.Role;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.repository.*;
import com.healthcare.appointment.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User patientUser;
    private User doctorUser;
    private User adminUser;

    @Autowired DoctorWorkingHoursRepository workingHoursRepository;
    @Autowired DoctorLeaveRepository doctorLeaveRepository;
    @Autowired SlotHoldRepository slotHoldRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired SymptomSubmissionRepository symptomRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AiSummaryRepository aiSummaryRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionItemRepository prescriptionItemRepository;
    @Autowired CalendarSyncRepository calendarSyncRepository;
    @Autowired CalendarCredentialRepository calendarCredentialRepository;

    @BeforeEach
    void setUp() {
        prescriptionItemRepository.deleteAll();
        consultationRepository.deleteAll();
        aiSummaryRepository.deleteAll();
        calendarSyncRepository.deleteAll();
        calendarCredentialRepository.deleteAll();
        notificationRepository.deleteAll();
        symptomRepository.deleteAll();
        slotHoldRepository.deleteAll();
        appointmentRepository.deleteAll();
        doctorLeaveRepository.deleteAll();
        workingHoursRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        patientUser = createUser("patient@test.com", "Patient@123", Role.PATIENT);
        Patient patient = new Patient();
        patient.setUser(patientUser);
        patientRepository.save(patient);

        doctorUser = createUser("doctor@test.com", "Doctor@123", Role.DOCTOR);
        Doctor doctor = new Doctor();
        doctor.setUser(doctorUser);
        doctor.setSpecialisation("Test Specialisation");
        doctorRepository.save(doctor);

        adminUser = createUser("admin@test.com", "Admin@123", Role.ADMIN);
    }

    // ── Test 1: Patient registration succeeds ────────────────────────────────

    @Test
    void register_validRequest_returnsTokenAndPatientRole() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Jane", "Doe", "jane@test.com", "SecurePass1", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("jane@test.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.patientId").isNotEmpty());
    }

    // ── Test 2: Duplicate email fails with 409 ────────────────────────────────

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Dup", "User", "patient@test.com", "SecurePass1", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── Test 3: Login succeeds ────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        LoginRequest req = new LoginRequest("patient@test.com", "Patient@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    // ── Test 4: Incorrect password returns 401 ────────────────────────────────

    @Test
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest("patient@test.com", "WrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ── Test 5: No token → 401 ───────────────────────────────────────────────

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/patient/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── Test 6: Valid JWT authenticates the request ──────────────────────────

    @Test
    void protectedEndpoint_validPatientToken_returns200() throws Exception {
        String token = jwtService.generateToken(patientUser);

        mockMvc.perform(get("/api/patient/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("patient@test.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    // ── Test 7: Patient cannot access admin endpoint ─────────────────────────

    @Test
    void adminEndpoint_patientToken_returns403() throws Exception {
        String token = jwtService.generateToken(patientUser);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── Test 8: Doctor cannot access admin endpoint ──────────────────────────

    @Test
    void adminEndpoint_doctorToken_returns403() throws Exception {
        String token = jwtService.generateToken(doctorUser);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── Test 9: Admin can access admin endpoint ───────────────────────────────

    @Test
    void adminEndpoint_adminToken_returns200() throws Exception {
        String token = jwtService.generateToken(adminUser);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── Test 10: Invalid JWT is rejected ─────────────────────────────────────

    @Test
    void protectedEndpoint_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/patient/me")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createUser(String email, String rawPassword, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(role);
        return userRepository.save(user);
    }
}
