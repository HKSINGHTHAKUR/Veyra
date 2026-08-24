package com.healthcare.appointment;

import com.healthcare.appointment.dto.AiSummaryResponse;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.integration.ai.FakeLlmClient;
import com.healthcare.appointment.repository.*;
import com.healthcare.appointment.security.JwtService;
import com.healthcare.appointment.security.UserPrincipal;
import com.healthcare.appointment.service.AiSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiSummaryServiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AiSummaryService aiSummaryService;
    @Autowired FakeLlmClient fakeLlmClient;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired DoctorWorkingHoursRepository workingHoursRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired SymptomSubmissionRepository symptomRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionItemRepository prescriptionItemRepository;
    @Autowired AiSummaryRepository aiSummaryRepository;
    @Autowired CalendarSyncRepository calendarSyncRepository;
    @Autowired CalendarCredentialRepository calendarCredentialRepository;
    @Autowired SlotHoldRepository slotHoldRepository;
    @Autowired DoctorLeaveRepository doctorLeaveRepository;
    @Autowired NotificationRepository notificationRepository;

    private Doctor doctor;
    private Patient patient, patient2;
    private User patientUser, patient2User, doctorUser;
    private Appointment appointment;
    private final LocalDate TEST_DATE = LocalDate.now().plusWeeks(6).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        notificationRepository.deleteAll();
        calendarSyncRepository.deleteAll();
        calendarCredentialRepository.deleteAll();
        aiSummaryRepository.deleteAll();
        prescriptionItemRepository.deleteAll();
        consultationRepository.deleteAll();
        symptomRepository.deleteAll();
        slotHoldRepository.deleteAll();
        appointmentRepository.deleteAll();
        doctorLeaveRepository.deleteAll();
        workingHoursRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        String suf = UUID.randomUUID().toString().substring(0,6);
        User du = buildUser("doc-ai-"+suf+"@test.com", Role.DOCTOR);
        doctor = new Doctor(); doctor.setUser(du); doctor.setSpecialisation("Cardiology"); doctor = doctorRepository.save(doctor);
        doctorUser = du;
        DoctorWorkingHours wh = new DoctorWorkingHours(); wh.setDoctor(doctor); wh.setDayOfWeek(TEST_DATE.getDayOfWeek()); wh.setStartTime(LocalTime.of(9,0)); wh.setEndTime(LocalTime.of(17,0)); wh.setSlotDurationMinutes(30); workingHoursRepository.save(wh);

        patientUser = buildUser("pat-ai-"+suf+"@test.com", Role.PATIENT);
        patient = new Patient(); patient.setUser(patientUser); patient = patientRepository.save(patient);
        patient2User = buildUser("pat2-ai-"+suf+"@test.com", Role.PATIENT);
        patient2 = new Patient(); patient2.setUser(patient2User); patient2 = patientRepository.save(patient2);

        // Create appointment with symptom
        appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentStart(TEST_DATE.atTime(10,0).toInstant(ZoneOffset.UTC));
        appointment.setAppointmentEnd(TEST_DATE.atTime(10,30).toInstant(ZoneOffset.UTC));
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment = appointmentRepository.save(appointment);

        SymptomSubmission ss = new SymptomSubmission();
        ss.setAppointment(appointment);
        ss.setPatient(patient);
        ss.setSymptomText("Persistent headache and fatigue for three days");
        symptomRepository.save(ss);
        SecurityContextHolder.clearContext();
    }

    private void asPatient(User u) {
        var principal = new UserPrincipal(u);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ── Pre-visit tests ──────────────────────────────────────────────────
    @Test
    void preVisit_validResponse_completed() {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.SUCCESS);
        AiSummaryResponse r = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("COMPLETED");
        assertThat(r.getUrgency()).isEqualTo("MEDIUM");
        assertThat(r.getChiefComplaint()).isNotBlank();
        assertThat(r.getSuggestedQuestions()).hasSize(3);
        assertThat(r.getSuggestedQuestions()).allMatch(q -> q != null && !q.isBlank());
    }

    @Test
    void preVisit_invalidUrgency_failed() {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.INVALID_URGENCY);
        AiSummaryResponse r = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("FAILED");
        assertThat(r.getFailureReason()).contains("Invalid urgency");
    }

    @Test
    void preVisit_missingChief_failed() {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.MISSING_CHIEF);
        AiSummaryResponse r = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void preVisit_wrongQuestionCount_failed() {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.WRONG_QUESTION_COUNT);
        AiSummaryResponse r = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void preVisit_timeout_failed() {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.TIMEOUT);
        AiSummaryResponse r = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("FAILED");
        // appointment must remain BOOKED
        Appointment a = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void preVisit_exception_failed_appointmentRemainsBooked() {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.EXCEPTION);
        AiSummaryResponse r = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("FAILED");
        Appointment a = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    // ── Post-visit tests ─────────────────────────────────────────────────
    @Test
    void postVisit_validResponse_completed() {
        createConsultationWithPrescription();
        asPatient(patientUser);
        fakeLlmClient.setPostVisitMode(FakeLlmClient.Mode.SUCCESS);
        AiSummaryResponse r = aiSummaryService.generatePostVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("COMPLETED");
        assertThat(r.getSummaryText()).isNotBlank();
        assertThat(r.getMedications()).hasSize(1);
        assertThat(r.getMedications().get(0).name()).isEqualTo("Amoxicillin");
        assertThat(r.getFollowUpSteps()).isNotEmpty();
    }

    @Test
    void postVisit_summaryStored() {
        createConsultationWithPrescription();
        fakeLlmClient.setPostVisitMode(FakeLlmClient.Mode.SUCCESS);
        AiSummaryResponse r = aiSummaryService.generatePostVisit(appointment.getId());
        assertThat(r.getSummaryText()).contains("consultation");
    }

    @Test
    void postVisit_prescriptionRepresentedCorrectly() {
        createConsultationWithPrescription();
        fakeLlmClient.setPostVisitMode(FakeLlmClient.Mode.SUCCESS);
        AiSummaryResponse r = aiSummaryService.generatePostVisit(appointment.getId());
        assertThat(r.getMedications()).anyMatch(m -> m.name().equals("Amoxicillin") && m.dosage().equals("500 mg"));
    }

    @Test
    void postVisit_followUpStepsStored() {
        createConsultationWithPrescription();
        fakeLlmClient.setPostVisitMode(FakeLlmClient.Mode.SUCCESS);
        AiSummaryResponse r = aiSummaryService.generatePostVisit(appointment.getId());
        assertThat(r.getFollowUpSteps()).hasSize(2);
    }

    @Test
    void postVisit_failure_markedFailed_consultationRemains() {
        createConsultationWithPrescription();
        fakeLlmClient.setPostVisitMode(FakeLlmClient.Mode.EXCEPTION_POST);
        AiSummaryResponse r = aiSummaryService.generatePostVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("FAILED");
        // consultation remains
        assertThat(consultationRepository.findByAppointmentId(appointment.getId())).isPresent();
        assertThat(consultationRepository.findByAppointmentId(appointment.getId()).get().getClinicalNotes()).isNotBlank();
    }

    // ── Duplicate protection ─────────────────────────────────────────────
    @Test
    void duplicate_preVisit_notDuplicated() {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.SUCCESS);
        AiSummaryResponse first = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(first.getStatus()).isEqualTo("COMPLETED");
        long countBefore = aiSummaryRepository.count();
        AiSummaryResponse second = aiSummaryService.generatePreVisit(appointment.getId());
        long countAfter = aiSummaryRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void duplicate_postVisit_notDuplicated() {
        createConsultationWithPrescription();
        fakeLlmClient.setPostVisitMode(FakeLlmClient.Mode.SUCCESS);
        AiSummaryResponse first = aiSummaryService.generatePostVisit(appointment.getId());
        long before = aiSummaryRepository.count();
        AiSummaryResponse second = aiSummaryService.generatePostVisit(appointment.getId());
        long after = aiSummaryRepository.count();
        assertThat(after).isEqualTo(before);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    // ── Failure isolation via booking flow ─────────────────────────────────
    @Test
    void failureIsolation_appointmentRemainsBookedWhenLlmFails() {
        // Simulate appointment confirmed, then pre-visit fails
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.EXCEPTION);
        AiSummaryResponse r = aiSummaryService.generatePreVisit(appointment.getId());
        assertThat(r.getStatus()).isEqualTo("FAILED");
        Appointment a = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    // ── Ownership tests via HTTP ─────────────────────────────────────────
    @Test
    void ownership_patientCanViewOwnPreVisit() throws Exception {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.SUCCESS);
        aiSummaryService.generatePreVisit(appointment.getId());
        clearAuth();
        String token = jwtService.generateToken(patientUser);
        mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/ai/pre-visit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void ownership_otherPatientCannotView() throws Exception {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.SUCCESS);
        aiSummaryService.generatePreVisit(appointment.getId());
        clearAuth();
        String token2 = jwtService.generateToken(patient2User);
        mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/ai/pre-visit")
                .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownership_doctorCanViewOwnAppointmentSummary() throws Exception {
        asPatient(patientUser);
        fakeLlmClient.setPreVisitMode(FakeLlmClient.Mode.SUCCESS);
        aiSummaryService.generatePreVisit(appointment.getId());
        clearAuth();
        String docToken = jwtService.generateToken(doctorUser);
        mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/ai/pre-visit")
                .header("Authorization", "Bearer " + docToken))
                .andExpect(status().isOk());
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void createConsultationWithPrescription() {
        Consultation c = new Consultation();
        c.setAppointment(appointment);
        c.setDoctor(doctor);
        c.setClinicalNotes("Patient reports headache, examination normal");
        c.setFollowUpInstructions("Return in one week");
        c.setConsultationTimestamp(Instant.now());
        c = consultationRepository.save(c);
        PrescriptionItem pi = new PrescriptionItem();
        pi.setConsultation(c);
        pi.setMedicationName("Amoxicillin");
        pi.setDosage("500 mg");
        pi.setFrequency("Twice daily");
        pi.setDurationDays(5);
        pi.setInstructions("Take with food");
        prescriptionItemRepository.save(pi);
    }

    private User buildUser(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@123"));
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        return userRepository.save(u);
    }
}
