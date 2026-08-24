package com.healthcare.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.integration.ai.FakeLlmClient;
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

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security hardening regressions (post-audit):
 *  - Consultation GET ownership (owning patient / assigned doctor / admin -> 200,
 *    anyone else -> 403 with NO clinical data in the body, unauthenticated -> 401)
 *  - AI generate endpoints: pre-visit = owning patient or ADMIN; post-visit = assigned
 *    doctor or ADMIN; unauthorized -> 403 and NOTHING persisted
 *  - MEDICATION_REMINDER created when a consultation carries prescriptions
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired DoctorWorkingHoursRepository workingHoursRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired SymptomSubmissionRepository symptomRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionItemRepository prescriptionItemRepository;
    @Autowired AiSummaryRepository aiSummaryRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SlotHoldRepository slotHoldRepository;
    @Autowired CalendarSyncRepository calendarSyncRepository;
    @Autowired CalendarCredentialRepository calendarCredentialRepository;
    @Autowired CalendarOauthStateRepository oauthStateRepository;
    @Autowired FakeLlmClient fakeLlmClient;

    private User ownerUser, adminUser, assignedDocUser;
    private Patient ownerPatient;
    private String ownerToken, otherPatientToken, adminToken, assignedDocToken, otherDocToken;
    private Appointment appointment;
    private final String CLINICAL_NOTES = "Confidential clinical notes: elevated blood pressure";

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        prescriptionItemRepository.deleteAll();
        consultationRepository.deleteAll();
        aiSummaryRepository.deleteAll();
        calendarSyncRepository.deleteAll();
        calendarCredentialRepository.deleteAll();
        oauthStateRepository.deleteAll();
        notificationRepository.deleteAll();
        symptomRepository.deleteAll();
        slotHoldRepository.deleteAll();
        appointmentRepository.deleteAll();
        workingHoursRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        String suf = java.util.UUID.randomUUID().toString().substring(0, 6);
        User a = saveUser("admin-h-" + suf + "@test.com", Role.ADMIN);
        adminToken = jwtService.generateToken(a);

        ownerUser = saveUser("owner-h-" + suf + "@test.com", Role.PATIENT);
        ownerPatient = new Patient(); ownerPatient.setUser(ownerUser); ownerPatient = patientRepository.save(ownerPatient);
        ownerToken = jwtService.generateToken(ownerUser);

        User opu = saveUser("other-p-h-" + suf + "@test.com", Role.PATIENT);
        Patient otherP = new Patient(); otherP.setUser(opu); patientRepository.save(otherP);
        otherPatientToken = jwtService.generateToken(opu);

        assignedDocUser = saveUser("doc-h-" + suf + "@test.com", Role.DOCTOR);
        Doctor doc = new Doctor(); doc.setUser(assignedDocUser); doc.setSpecialisation("Cardiology");
        doc = doctorRepository.save(doc);
        assignedDocToken = jwtService.generateToken(assignedDocUser);

        User odu = saveUser("doc2-h-" + suf + "@test.com", Role.DOCTOR);
        Doctor doc2 = new Doctor(); doc2.setUser(odu); doc2.setSpecialisation("Neurology");
        doctorRepository.save(doc2);
        otherDocToken = jwtService.generateToken(odu);

        appointment = new Appointment();
        appointment.setPatient(ownerPatient);
        appointment.setDoctor(doc);
        appointment.setAppointmentStart(LocalDate.now().plusWeeks(5)
                .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(10, 0).toInstant(ZoneOffset.UTC));
        appointment.setAppointmentEnd(LocalDate.now().plusWeeks(5)
                .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(10, 30).toInstant(ZoneOffset.UTC));
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment = appointmentRepository.save(appointment);

        SymptomSubmission ss = new SymptomSubmission();
        ss.setAppointment(appointment); ss.setPatient(ownerPatient); ss.setSymptomText("headache");
        symptomRepository.save(ss);

        // Consultation + prescription exist so post-visit generation can succeed
        Consultation c = new Consultation();
        c.setAppointment(appointment); c.setDoctor(doc);
        c.setClinicalNotes(CLINICAL_NOTES); c.setConsultationTimestamp(Instant.now());
        c = consultationRepository.save(c);
        PrescriptionItem pi = new PrescriptionItem();
        pi.setConsultation(c); pi.setMedicationName("Secretolol"); pi.setDosage("10 mg");
        pi.setFrequency("Once daily"); pi.setDurationDays(7);
        prescriptionItemRepository.save(pi);
    }

    private String bodyOf(org.springframework.test.web.servlet.MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    // â”€â”€ CONSULTATION GET â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void cons01_owningPatient_200() throws Exception {
        mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/consultation")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicalNotes").value(CLINICAL_NOTES));
    }

    @Test
    void cons02_assignedDoctor_200() throws Exception {
        mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/consultation")
                        .header("Authorization", "Bearer " + assignedDocToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicalNotes").value(CLINICAL_NOTES));
    }

    @Test
    void cons03_admin_200() throws Exception {
        mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/consultation")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicalNotes").value(CLINICAL_NOTES));
    }

    @Test
    void cons04_unrelatedPatient_403_noDataLeak() throws Exception {
        var res = mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/consultation")
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden())
                .andReturn();
        // Explicit data-leakage assertion: forbidden caller must receive NEITHER the
        // clinical notes NOR any prescription information.
        assertThat(bodyOf(res)).doesNotContain("Confidential clinical notes");
        assertThat(bodyOf(res)).doesNotContain("Secretolol");
        assertThat(bodyOf(res)).doesNotContain("clinicalNotes");
        assertThat(bodyOf(res)).doesNotContain("prescriptions");
    }

    @Test
    void cons05_unrelatedDoctor_403_noDataLeak() throws Exception {
        var res = mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/consultation")
                        .header("Authorization", "Bearer " + otherDocToken))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(bodyOf(res)).doesNotContain("Confidential clinical notes");
        assertThat(bodyOf(res)).doesNotContain("Secretolol");
        assertThat(bodyOf(res)).doesNotContain("clinicalNotes");
        assertThat(bodyOf(res)).doesNotContain("prescriptions");
    }

    @Test
    void cons06_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/appointments/" + appointment.getId() + "/consultation"))
                .andExpect(status().isUnauthorized());
    }

    // â”€â”€ AI PRE-VISIT GENERATE (owning PATIENT or ADMIN only) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void ai07_preVisit_owningPatient_success() throws Exception {
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/pre-visit/generate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(aiSummaryRepository.count()).isEqualTo(1);
    }

    @Test
    void ai08_preVisit_unrelatedPatient_403_nothingPersisted() throws Exception {
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/pre-visit/generate")
                        .header("Authorization", "Bearer " + otherPatientToken))
                .andExpect(status().isForbidden());
        assertThat(aiSummaryRepository.count()).isEqualTo(0);
    }

    @Test
    void ai09_preVisit_admin_success() throws Exception {
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/pre-visit/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void ai10_preVisit_doctor_forbidden_byPolicy() throws Exception {
        // Business policy: doctors do NOT trigger pre-visit generation (patient/admin only)
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/pre-visit/generate")
                        .header("Authorization", "Bearer " + assignedDocToken))
                .andExpect(status().isForbidden());
        assertThat(aiSummaryRepository.count()).isEqualTo(0);
    }

    // â”€â”€ AI POST-VISIT GENERATE (assigned DOCTOR or ADMIN only) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void ai11_postVisit_assignedDoctor_success() throws Exception {
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/post-visit/generate")
                        .header("Authorization", "Bearer " + assignedDocToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(aiSummaryRepository.count()).isEqualTo(1);
    }

    @Test
    void ai12_postVisit_unrelatedDoctor_403_nothingPersisted() throws Exception {
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/post-visit/generate")
                        .header("Authorization", "Bearer " + otherDocToken))
                .andExpect(status().isForbidden());
        assertThat(aiSummaryRepository.count()).isEqualTo(0);
    }

    @Test
    void ai13_postVisit_patient_forbidden_byPolicy() throws Exception {
        // Business policy: patients do NOT trigger post-visit generation (doctor/admin only)
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/post-visit/generate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
        assertThat(aiSummaryRepository.count()).isEqualTo(0);
    }

    @Test
    void ai14_postVisit_admin_success() throws Exception {
        mockMvc.perform(post("/api/appointments/" + appointment.getId() + "/ai/post-visit/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // â”€â”€ MEDICATION_REMINDER minimal implementation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void medReminder_createdWithPrescriptions_noneWithout() throws Exception {
        // Positive case: consultation WITH prescriptions -> MEDICATION_REMINDER PENDING
        User du2 = saveUser("doc-m-" + java.util.UUID.randomUUID().toString().substring(0,4) + "@test.com", Role.DOCTOR);
        Doctor doc2 = new Doctor(); doc2.setUser(du2); doc2.setSpecialisation("ENT"); doc2 = doctorRepository.save(doc2);
        Appointment apptRx = new Appointment();
        apptRx.setPatient(ownerPatient); apptRx.setDoctor(doc2);
        apptRx.setAppointmentStart(appointment.getAppointmentStart().plusSeconds(86400));
        apptRx.setAppointmentEnd(appointment.getAppointmentEnd().plusSeconds(86400));
        apptRx.setStatus(AppointmentStatus.BOOKED);
        apptRx = appointmentRepository.save(apptRx);

        String rxBody = "{\"clinicalNotes\":\"note\",\"prescriptions\":[{\"medicationName\":\"Testorin\",\"dosage\":\"5 mg\",\"frequency\":\"daily\",\"durationDays\":3}]}";
        mockMvc.perform(post("/api/appointments/" + apptRx.getId() + "/consultation")
                        .header("Authorization", "Bearer " + jwtService.generateToken(du2))
                        .contentType(MediaType.APPLICATION_JSON).content(rxBody))
                .andExpect(status().isCreated());

        var reminders = notificationRepository.findByAppointmentId(apptRx.getId()).stream()
                .filter(n -> n.getType() == NotificationType.MEDICATION_REMINDER).toList();
        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(reminders.get(0).getBody()).contains("Testorin");

        // Negative case: consultation without prescriptions -> no reminder
        String noRxBody = "{\"clinicalNotes\":\"no meds\"}";
        User du3 = saveUser("doc-n-" + java.util.UUID.randomUUID().toString().substring(0,4) + "@test.com", Role.DOCTOR);
        Doctor doc3 = new Doctor(); doc3.setUser(du3); doc3.setSpecialisation("Derma"); doc3 = doctorRepository.save(doc3);
        Appointment apptNoRx2 = new Appointment();
        apptNoRx2.setPatient(ownerPatient); apptNoRx2.setDoctor(doc3);
        apptNoRx2.setAppointmentStart(appointment.getAppointmentStart().plusSeconds(172800));
        apptNoRx2.setAppointmentEnd(appointment.getAppointmentEnd().plusSeconds(172800));
        apptNoRx2.setStatus(AppointmentStatus.BOOKED);
        apptNoRx2 = appointmentRepository.save(apptNoRx2);
        mockMvc.perform(post("/api/appointments/" + apptNoRx2.getId() + "/consultation")
                        .header("Authorization", "Bearer " + jwtService.generateToken(du3))
                        .contentType(MediaType.APPLICATION_JSON).content(noRxBody))
                .andExpect(status().isCreated());
        assertThat(notificationRepository.findByAppointmentId(apptNoRx2.getId()))
                .noneMatch(n -> n.getType() == NotificationType.MEDICATION_REMINDER);
    }

    private User saveUser(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@123"));
        u.setFirstName("T"); u.setLastName("U"); u.setRole(role);
        return userRepository.save(u);
    }
}
