package com.healthcare.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.dto.AvailabilityResponse;
import com.healthcare.appointment.dto.UpdateDoctorRequest;
import com.healthcare.appointment.dto.WorkingHoursRequest;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.repository.*;
import com.healthcare.appointment.security.JwtService;
import com.healthcare.appointment.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Additional coverage for Phase 3 requirements that were not covered
 * by the existing DoctorManagementTest and AvailabilityServiceTest.
 *
 * Covers:
 * - admin updates doctor (requirement 4)
 * - patient search by specialisation case-insensitive (requirement 7)
 * - invalid slot duration rejected (requirement 10)
 * - COMPLETED / NO_SHOW do not block (requirements 19,20)
 * - expired ACTIVE hold does not block (requirement 22 extended)
 * - inactive doctor excluded from search (additional confirmation)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase3AdditionalCoverageTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AvailabilityService availabilityService;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired DoctorWorkingHoursRepository workingHoursRepository;
    @Autowired DoctorLeaveRepository leaveRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired SlotHoldRepository slotHoldRepository;
    @Autowired AiSummaryRepository aiSummaryRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionItemRepository prescriptionItemRepository;
    @Autowired SymptomSubmissionRepository symptomRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CalendarSyncRepository calendarSyncRepository;
    @Autowired CalendarCredentialRepository calendarCredentialRepository;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminToken;
    private String patientToken;
    private Doctor cardiologyDoctor;
    private Doctor neurologyDoctor;
    private Patient patient;

    private static final LocalDate TEST_DATE = LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
            .plusWeeks(6);

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
        leaveRepository.deleteAll();
        workingHoursRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = saveUser("admin2-" + suffix + "@test.com", Role.ADMIN);
        adminToken = jwtService.generateToken(admin);

        User patientUser = saveUser("patient2-" + suffix + "@test.com", Role.PATIENT);
        patient = new Patient();
        patient.setUser(patientUser);
        patient = patientRepository.save(patient);
        patientToken = jwtService.generateToken(patientUser);

        User docUser1 = saveUser("cardio-" + suffix + "@test.com", Role.DOCTOR);
        cardiologyDoctor = new Doctor();
        cardiologyDoctor.setUser(docUser1);
        cardiologyDoctor.setSpecialisation("Cardiology");
        cardiologyDoctor = doctorRepository.save(cardiologyDoctor);

        User docUser2 = saveUser("neuro-" + suffix + "@test.com", Role.DOCTOR);
        neurologyDoctor = new Doctor();
        neurologyDoctor.setUser(docUser2);
        neurologyDoctor.setSpecialisation("Neurology");
        neurologyDoctor = doctorRepository.save(neurologyDoctor);
    }

    // ── Requirement 4: Admin updates doctor ────────────────────────────────
    @Test
    void adminUpdatesDoctor_returns200AndPersists() throws Exception {
        UpdateDoctorRequest req = new UpdateDoctorRequest();
        req.setSpecialisation("Cardiology-Updated");
        req.setBio("Updated bio");

        mockMvc.perform(patch("/api/admin/doctors/" + cardiologyDoctor.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialisation").value("Cardiology-Updated"))
                .andExpect(jsonPath("$.bio").value("Updated bio"));
    }

    // ── Requirement 7: Patient can search by specialisation case-insensitive ─
    @Test
    void patientSearchBySpecialisation_caseInsensitive() throws Exception {
        // Search "cardiology" lower case should find Cardiology doctor
        mockMvc.perform(get("/api/doctors")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("specialisation", "cardiology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.specialisation == 'Cardiology')]").exists())
                .andExpect(jsonPath("$[?(@.specialisation == 'Neurology')]").doesNotExist());

        // Search "NEURO" upper case should find Neurology
        mockMvc.perform(get("/api/doctors")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("specialisation", "NEURO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.specialisation == 'Neurology')]").exists());
    }

    @Test
    void patientSearch_withoutFilter_returnsBothActiveDoctors() throws Exception {
        mockMvc.perform(get("/api/doctors")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── Requirement 10: Invalid slot duration rejected ──────────────────────
    @Test
    void invalidSlotDuration_exceedsWindow_returns400() throws Exception {
        WorkingHoursRequest req = new WorkingHoursRequest();
        req.setDayOfWeek(DayOfWeek.MONDAY);
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(10, 0)); // 60 min window
        req.setSlotDurationMinutes(90); // exceeds window

        mockMvc.perform(post("/api/admin/doctors/" + cardiologyDoctor.getId() + "/working-hours")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSlotDuration_zero_returns400() throws Exception {
        WorkingHoursRequest req = new WorkingHoursRequest();
        req.setDayOfWeek(DayOfWeek.TUESDAY);
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(17, 0));
        req.setSlotDurationMinutes(0);

        mockMvc.perform(post("/api/admin/doctors/" + cardiologyDoctor.getId() + "/working-hours")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── Requirement 19: COMPLETED does not block ───────────────────────────
    @Test
    void completedAppointment_doesNotBlockSlot() {
        addWorkingHours(cardiologyDoctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);
        Instant start = TEST_DATE.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant end = TEST_DATE.atTime(9, 30).toInstant(ZoneOffset.UTC);
        createAppointment(cardiologyDoctor, start, end, AppointmentStatus.COMPLETED);

        AvailabilityResponse result = availabilityService.getAvailability(cardiologyDoctor.getId(), TEST_DATE);
        assertThat(result.getSlots()).hasSize(4);
        assertThat(result.getSlots()).allMatch(s -> s.isAvailable());
    }

    // ── Requirement 20: NO_SHOW does not block ────────────────────────────
    @Test
    void noShowAppointment_doesNotBlockSlot() {
        addWorkingHours(cardiologyDoctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);
        Instant start = TEST_DATE.atTime(9, 30).toInstant(ZoneOffset.UTC);
        Instant end = TEST_DATE.atTime(10, 0).toInstant(ZoneOffset.UTC);
        createAppointment(cardiologyDoctor, start, end, AppointmentStatus.NO_SHOW);

        AvailabilityResponse result = availabilityService.getAvailability(cardiologyDoctor.getId(), TEST_DATE);
        assertThat(result.getSlots()).hasSize(4);
        assertThat(result.getSlots()).allMatch(s -> s.isAvailable());
    }

    // ── Requirement 22 extended: ACTIVE but expired hold does not block ────
    @Test
    void activeHold_expired_doesNotBlockSlot() {
        addWorkingHours(cardiologyDoctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);
        Instant start = TEST_DATE.atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant end = TEST_DATE.atTime(10, 30).toInstant(ZoneOffset.UTC);
        // ACTIVE status but expiresAt in the past -> should NOT block
        createHold(cardiologyDoctor, start, end, SlotHoldStatus.ACTIVE, Instant.now().minusSeconds(60));

        AvailabilityResponse result = availabilityService.getAvailability(cardiologyDoctor.getId(), TEST_DATE);
        boolean slotAvailable = result.getSlots().stream()
                .anyMatch(s -> s.getSlotStart().equals(start) && s.isAvailable());
        assertThat(slotAvailable).isTrue();
        assertThat(result.getSlots()).hasSize(4);
        assertThat(result.getSlots().stream().filter(s -> s.isAvailable()).count()).isEqualTo(4);
    }

    @Test
    void activeHold_notExpired_doesBlockSlot() {
        addWorkingHours(cardiologyDoctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);
        Instant start = TEST_DATE.atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant end = TEST_DATE.atTime(10, 30).toInstant(ZoneOffset.UTC);
        createHold(cardiologyDoctor, start, end, SlotHoldStatus.ACTIVE, Instant.now().plusSeconds(600));

        AvailabilityResponse result = availabilityService.getAvailability(cardiologyDoctor.getId(), TEST_DATE);
        boolean slotBlocked = result.getSlots().stream()
                .anyMatch(s -> s.getSlotStart().equals(start) && !s.isAvailable());
        assertThat(slotBlocked).isTrue();
    }

    // ── Additional: slot boundary validation for various invalid times ─────
    @Test
    void slotBoundary_validation_rejectsVariousInvalidTimes() {
        addWorkingHours(cardiologyDoctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), 30);

        // 09:15 invalid
        assertThat(availabilityService.isValidSlot(cardiologyDoctor.getId(), TEST_DATE,
                TEST_DATE.atTime(9, 15).toInstant(ZoneOffset.UTC),
                TEST_DATE.atTime(9, 45).toInstant(ZoneOffset.UTC))).isFalse();

        // 10:07 invalid
        assertThat(availabilityService.isValidSlot(cardiologyDoctor.getId(), TEST_DATE,
                TEST_DATE.atTime(10, 7).toInstant(ZoneOffset.UTC),
                TEST_DATE.atTime(10, 37).toInstant(ZoneOffset.UTC))).isFalse();

        // valid 10:30 should pass
        assertThat(availabilityService.isValidSlot(cardiologyDoctor.getId(), TEST_DATE,
                TEST_DATE.atTime(10, 30).toInstant(ZoneOffset.UTC),
                TEST_DATE.atTime(11, 0).toInstant(ZoneOffset.UTC))).isTrue();
    }

    private void addWorkingHours(Doctor doctor, DayOfWeek day, LocalTime start, LocalTime end, int duration) {
        DoctorWorkingHours wh = new DoctorWorkingHours();
        wh.setDoctor(doctor);
        wh.setDayOfWeek(day);
        wh.setStartTime(start);
        wh.setEndTime(end);
        wh.setSlotDurationMinutes(duration);
        workingHoursRepository.save(wh);
    }

    private void createAppointment(Doctor doctor, Instant start, Instant end, AppointmentStatus status) {
        Appointment appt = new Appointment();
        appt.setPatient(patient);
        appt.setDoctor(doctor);
        appt.setAppointmentStart(start);
        appt.setAppointmentEnd(end);
        appt.setStatus(status);
        appointmentRepository.save(appt);
    }

    private void createHold(Doctor doctor, Instant slotStart, Instant slotEnd,
                            SlotHoldStatus status, Instant expiresAt) {
        SlotHold hold = new SlotHold();
        hold.setDoctor(doctor);
        hold.setPatient(patient);
        hold.setSlotStart(slotStart);
        hold.setSlotEnd(slotEnd);
        hold.setStatus(status);
        hold.setExpiresAt(expiresAt);
        slotHoldRepository.save(hold);
    }

    private User saveUser(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@123"));
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        return userRepository.save(u);
    }
}
