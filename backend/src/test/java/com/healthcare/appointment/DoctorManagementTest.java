package com.healthcare.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.dto.CreateDoctorRequest;
import com.healthcare.appointment.dto.LeaveRequest;
import com.healthcare.appointment.dto.WorkingHoursRequest;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests 1–11: Doctor management, working hours, and leave via HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoctorManagementTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired DoctorWorkingHoursRepository workingHoursRepository;
    @Autowired DoctorLeaveRepository leaveRepository;
    @Autowired SlotHoldRepository slotHoldRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired SymptomSubmissionRepository symptomRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AiSummaryRepository aiSummaryRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionItemRepository prescriptionItemRepository;
    @Autowired CalendarSyncRepository calendarSyncRepository;
    @Autowired CalendarCredentialRepository calendarCredentialRepository;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminToken;
    private String patientToken;
    private String doctorToken;
    private Doctor existingDoctor;

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

        User admin = save("admin@test.com", Role.ADMIN);
        adminToken = jwtService.generateToken(admin);

        User patientUser = save("patient@test.com", Role.PATIENT);
        Patient patient = new Patient();
        patient.setUser(patientUser);
        patientRepository.save(patient);
        patientToken = jwtService.generateToken(patientUser);

        User doctorUser = save("doctor@test.com", Role.DOCTOR);
        existingDoctor = new Doctor();
        existingDoctor.setUser(doctorUser);
        existingDoctor.setSpecialisation("Cardiology");
        existingDoctor = doctorRepository.save(existingDoctor);
        doctorToken = jwtService.generateToken(doctorUser);
    }

    // ── Test 1: Admin can create doctor ──────────────────────────────────────

    @Test
    void test1_adminCreatesDoctor_returns201() throws Exception {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setFirstName("New");
        req.setLastName("Doctor");
        req.setEmail("newdoc@test.com");
        req.setSpecialisation("Neurology");
        req.setTemporaryPassword("Temp@1234");

        mockMvc.perform(post("/api/admin/doctors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newdoc@test.com"))
                .andExpect(jsonPath("$.specialisation").value("Neurology"));
    }

    // ── Test 2: Patient cannot create doctor ──────────────────────────────────

    @Test
    void test2_patientCreatesDoctor_returns403() throws Exception {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setFirstName("X"); req.setLastName("Y");
        req.setEmail("x@test.com");
        req.setSpecialisation("ENT");
        req.setTemporaryPassword("Temp@1234");

        mockMvc.perform(post("/api/admin/doctors")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ── Test 3: Doctor cannot create doctor ───────────────────────────────────

    @Test
    void test3_doctorCreatesDoctor_returns403() throws Exception {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setFirstName("X"); req.setLastName("Y");
        req.setEmail("another@test.com");
        req.setSpecialisation("ENT");
        req.setTemporaryPassword("Temp@1234");

        mockMvc.perform(post("/api/admin/doctors")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ── Test 4: Admin can deactivate doctor ───────────────────────────────────

    @Test
    void test4_adminDeactivatesDoctor_returns200WithActiveFalse() throws Exception {
        mockMvc.perform(patch("/api/admin/doctors/" + existingDoctor.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ── Test 5: Inactive doctor not returned in patient search ────────────────

    @Test
    void test5_inactiveDoctor_notInPatientSearch() throws Exception {
        // Deactivate
        existingDoctor.setActive(false);
        doctorRepository.save(existingDoctor);
        existingDoctor.getUser().setActive(false);
        userRepository.save(existingDoctor.getUser());

        mockMvc.perform(get("/api/doctors")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == '" + existingDoctor.getId() + "')]").doesNotExist());
    }

    // ── Test 6: Admin can configure working hours ─────────────────────────────

    @Test
    void test6_adminSetsWorkingHours_returns201() throws Exception {
        WorkingHoursRequest req = new WorkingHoursRequest();
        req.setDayOfWeek(DayOfWeek.MONDAY);
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(17, 0));
        req.setSlotDurationMinutes(30);

        mockMvc.perform(post("/api/admin/doctors/" + existingDoctor.getId() + "/working-hours")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.slotDurationMinutes").value(30));
    }

    // ── Test 7: Invalid start/end rejected ───────────────────────────────────

    @Test
    void test7_invalidWorkingHoursTimes_returns400() throws Exception {
        WorkingHoursRequest req = new WorkingHoursRequest();
        req.setDayOfWeek(DayOfWeek.TUESDAY);
        req.setStartTime(LocalTime.of(17, 0));   // start after end
        req.setEndTime(LocalTime.of(9, 0));
        req.setSlotDurationMinutes(30);

        mockMvc.perform(post("/api/admin/doctors/" + existingDoctor.getId() + "/working-hours")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── Test 8: Duplicate doctor/day schedule rejected ────────────────────────

    @Test
    void test8_duplicateWorkingHours_returns409() throws Exception {
        WorkingHoursRequest req = new WorkingHoursRequest();
        req.setDayOfWeek(DayOfWeek.WEDNESDAY);
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(17, 0));
        req.setSlotDurationMinutes(30);

        String body = objectMapper.writeValueAsString(req);
        String path = "/api/admin/doctors/" + existingDoctor.getId() + "/working-hours";

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second call for same day must fail
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ── Test 9: Admin can add leave ───────────────────────────────────────────

    @Test
    void test9_adminAddsLeave_returns201() throws Exception {
        LeaveRequest req = new LeaveRequest();
        req.setLeaveDate(LocalDate.of(2027, 1, 15));
        req.setReason("Conference");

        mockMvc.perform(post("/api/admin/doctors/" + existingDoctor.getId() + "/leaves")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveDate").value("2027-01-15"));
    }

    // ── Test 10: Duplicate leave rejected ────────────────────────────────────

    @Test
    void test10_duplicateLeave_returns409() throws Exception {
        LeaveRequest req = new LeaveRequest();
        req.setLeaveDate(LocalDate.of(2027, 2, 20));

        String body = objectMapper.writeValueAsString(req);
        String path = "/api/admin/doctors/" + existingDoctor.getId() + "/leaves";

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    // ── Test 11: Leave date makes availability empty ──────────────────────────

    @Test
    void test11_leaveDate_availabilityIsEmpty() throws Exception {
        // Set up working hours for MONDAY
        WorkingHoursRequest wh = new WorkingHoursRequest();
        wh.setDayOfWeek(DayOfWeek.MONDAY);
        wh.setStartTime(LocalTime.of(9, 0));
        wh.setEndTime(LocalTime.of(17, 0));
        wh.setSlotDurationMinutes(30);
        mockMvc.perform(post("/api/admin/doctors/" + existingDoctor.getId() + "/working-hours")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wh)))
                .andExpect(status().isCreated());

        // Find a future Monday
        LocalDate monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Add leave on that Monday
        LeaveRequest leave = new LeaveRequest();
        leave.setLeaveDate(monday);
        mockMvc.perform(post("/api/admin/doctors/" + existingDoctor.getId() + "/leaves")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leave)))
                .andExpect(status().isCreated());

        // Availability should be empty
        mockMvc.perform(get("/api/doctors/" + existingDoctor.getId() + "/availability")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("date", monday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots").isArray())
                .andExpect(jsonPath("$.slots").isEmpty());
    }

    private User save(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@123"));
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        return userRepository.save(u);
    }
}
