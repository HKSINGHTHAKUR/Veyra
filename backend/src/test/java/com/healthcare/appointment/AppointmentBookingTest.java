package com.healthcare.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.entity.*;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentBookingTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
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

    private String patientToken, patient2Token, doctorToken, adminToken;
    private Doctor doctor;
    private Doctor doctor2;
    private Patient patient;
    private Patient patient2;
    private final LocalDate TEST_DATE = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(5);

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

        String suf = UUID.randomUUID().toString().substring(0,6);
        User admin = saveUser("admin-"+suf+"@test.com", Role.ADMIN);
        adminToken = jwtService.generateToken(admin);

        User pu = saveUser("pat-"+suf+"@test.com", Role.PATIENT);
        patient = new Patient(); patient.setUser(pu); patient = patientRepository.save(patient);
        patientToken = jwtService.generateToken(pu);

        User pu2 = saveUser("pat2-"+suf+"@test.com", Role.PATIENT);
        patient2 = new Patient(); patient2.setUser(pu2); patient2 = patientRepository.save(patient2);
        patient2Token = jwtService.generateToken(pu2);

        User du = saveUser("doc-"+suf+"@test.com", Role.DOCTOR);
        doctor = new Doctor(); doctor.setUser(du); doctor.setSpecialisation("Cardiology"); doctor = doctorRepository.save(doctor);
        doctorToken = jwtService.generateToken(du);

        User du2 = saveUser("doc2-"+suf+"@test.com", Role.DOCTOR);
        doctor2 = new Doctor(); doctor2.setUser(du2); doctor2.setSpecialisation("Neurology"); doctor2 = doctorRepository.save(doctor2);

        // Working hours Monday 09-17 30min for doctor
        DoctorWorkingHours wh = new DoctorWorkingHours();
        wh.setDoctor(doctor); wh.setDayOfWeek(DayOfWeek.MONDAY); wh.setStartTime(LocalTime.of(9,0)); wh.setEndTime(LocalTime.of(17,0)); wh.setSlotDurationMinutes(30);
        workingHoursRepository.save(wh);
        DoctorWorkingHours wh2 = new DoctorWorkingHours();
        wh2.setDoctor(doctor2); wh2.setDayOfWeek(DayOfWeek.MONDAY); wh2.setStartTime(LocalTime.of(9,0)); wh2.setEndTime(LocalTime.of(17,0)); wh2.setSlotDurationMinutes(30);
        workingHoursRepository.save(wh2);
    }

    private Instant slot(int hour, int min) { return TEST_DATE.atTime(hour,min).toInstant(ZoneOffset.UTC); }

    private UUID createHoldAndReturnId(String token, UUID doctorId, Instant slotStart) throws Exception {
        String body = "{\"doctorId\":\""+doctorId+"\",\"slotStart\":\""+slotStart.toString()+"\"}";
        MvcResult r = mockMvc.perform(post("/api/appointments/hold")
                .header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        return UUID.fromString(json.get("holdId").asText());
    }

    // 1
    @Test
    void test01_patientCanHoldAvailableSlot() throws Exception {
        createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
    }
    // 2
    @Test
    void test02_doctorCannotCreateHold() throws Exception {
        String body = "{\"doctorId\":\""+doctor.getId()+"\",\"slotStart\":\""+slot(10,0)+"\"}";
        mockMvc.perform(post("/api/appointments/hold").header("Authorization","Bearer "+doctorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
    //3
    @Test
    void test03_patientCannotHoldInvalidSlot() throws Exception {
        String body = "{\"doctorId\":\""+doctor.getId()+"\",\"slotStart\":\""+TEST_DATE.atTime(9,7).toInstant(ZoneOffset.UTC)+"\"}";
        mockMvc.perform(post("/api/appointments/hold").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
    //4
    @Test
    void test04_patientCannotHoldDuringLeave() throws Exception {
        DoctorLeave leave = new DoctorLeave(); leave.setDoctor(doctor); leave.setLeaveDate(TEST_DATE); leave.setReason("leave"); doctorLeaveRepository.save(leave);
        String body = "{\"doctorId\":\""+doctor.getId()+"\",\"slotStart\":\""+slot(10,0)+"\"}";
        mockMvc.perform(post("/api/appointments/hold").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
    //5
    @Test
    void test05_patientCannotHoldBookedSlot() throws Exception {
        Appointment a = new Appointment(); a.setPatient(patient); a.setDoctor(doctor); a.setAppointmentStart(slot(10,0)); a.setAppointmentEnd(slot(10,30)); a.setStatus(AppointmentStatus.BOOKED); appointmentRepository.save(a);
        String body = "{\"doctorId\":\""+doctor.getId()+"\",\"slotStart\":\""+slot(10,0)+"\"}";
        mockMvc.perform(post("/api/appointments/hold").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
    //6
    @Test
    void test06_patientCannotHoldActiveHeldSlot() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        assertThat(holdId).isNotNull();
        String body = "{\"doctorId\":\""+doctor.getId()+"\",\"slotStart\":\""+slot(10,0)+"\"}";
        mockMvc.perform(post("/api/appointments/hold").header("Authorization","Bearer "+patient2Token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
    //7
    @Test
    void test07_expiredHoldDoesNotBlockNewHold() throws Exception {
        SlotHold hold = new SlotHold(); hold.setDoctor(doctor); hold.setPatient(patient2); hold.setSlotStart(slot(10,0)); hold.setSlotEnd(slot(10,30)); hold.setStatus(SlotHoldStatus.ACTIVE); hold.setExpiresAt(Instant.now().minusSeconds(60)); slotHoldRepository.save(hold);
        // Now patient can hold same slot
        UUID newHold = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        assertThat(newHold).isNotNull();
    }
    //8 hold expiration handled – expired ACTIVE not blocking availability
    @Test
    void test08_holdExpirationNotBlockingAvailability() throws Exception {
        SlotHold hold = new SlotHold(); hold.setDoctor(doctor); hold.setPatient(patient2); hold.setSlotStart(slot(10,0)); hold.setSlotEnd(slot(10,30)); hold.setStatus(SlotHoldStatus.ACTIVE); hold.setExpiresAt(Instant.now().minusSeconds(60)); slotHoldRepository.save(hold);
        // Availability should still allow new hold on same slot (expired not blocking)
        UUID newHold = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        assertThat(newHold).isNotNull();
        // Old hold should now be EXPIRED (service expires inline before inserting new)
        SlotHold updated = slotHoldRepository.findById(hold.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SlotHoldStatus.EXPIRED);
    }
    //9 cannot access another patient's hold + GET hold ownership 403
    @Test
    void test09_patientCannotAccessAnotherHold() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        // patient2 tries to submit symptoms
        String body = "{\"symptoms\":\"headache\"}";
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms").header("Authorization","Bearer "+patient2Token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        // patient2 cannot GET hold
        mockMvc.perform(get("/api/appointments/holds/"+holdId).header("Authorization","Bearer "+patient2Token))
                .andExpect(status().isForbidden());
        // owner can GET
        mockMvc.perform(get("/api/appointments/holds/"+holdId).header("Authorization","Bearer "+patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdId").value(holdId.toString()));
        // admin can GET
        mockMvc.perform(get("/api/appointments/holds/"+holdId).header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk());
    }
    //10 patient can submit symptoms for own hold
    @Test
    void test10_patientCanSubmitSymptoms() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        String body = "{\"symptoms\":\"Persistent headache for 3 days\"}";
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symptomText").value("Persistent headache for 3 days"));
    }
    //11 duplicate of 9 already
    @Test
    void test11_patientCannotSubmitSymptomsForOtherHold() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(11,0));
        String body = "{\"symptoms\":\"test\"}";
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms").header("Authorization","Bearer "+patient2Token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }
    //12 expired hold cannot receive symptoms
    @Test
    void test12_expiredHoldCannotReceiveSymptoms() throws Exception {
        SlotHold hold = new SlotHold(); hold.setDoctor(doctor); hold.setPatient(patient); hold.setSlotStart(slot(10,0)); hold.setSlotEnd(slot(10,30)); hold.setStatus(SlotHoldStatus.ACTIVE); hold.setExpiresAt(Instant.now().minusSeconds(10)); hold = slotHoldRepository.save(hold);
        String body = "{\"symptoms\":\"test\"}";
        mockMvc.perform(post("/api/appointments/"+hold.getId()+"/symptoms").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
    //13 valid hold becomes appointment
    @Test
    void test13_validHoldBecomesAppointment() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"headache\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+holdId+"/confirm").header("Authorization","Bearer "+patientToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn();
        JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
        UUID apptId = UUID.fromString(json.get("id").asText());
        assertThat(apptId).isNotNull();
        // check hold converted
        SlotHold hold = slotHoldRepository.findById(holdId).orElseThrow();
        assertThat(hold.getStatus()).isEqualTo(SlotHoldStatus.CONVERTED);
        // check appointment status
        Appointment appt = appointmentRepository.findById(apptId).orElseThrow();
        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }
    //16 symptoms associated
    @Test
    void test16_symptomsAssociated() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"my symptoms\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+holdId+"/confirm").header("Authorization","Bearer "+patientToken))
                .andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        MvcResult get = mockMvc.perform(get("/api/appointments/"+apptId).header("Authorization","Bearer "+patientToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(get.getResponse().getContentAsString());
        assertThat(node.get("symptomText").asText()).isEqualTo("my symptoms");
    }
    //17 notification created
    @Test
    void test17_notificationCreated() throws Exception {
        long before = notificationRepository.count();
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,30));
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"x\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+holdId+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated());
        long after = notificationRepository.count();
        assertThat(after).isGreaterThan(before);
    }
    //18 expired hold cannot be confirmed
    @Test
    void test18_expiredHoldCannotBeConfirmed() throws Exception {
        SlotHold hold = new SlotHold(); hold.setDoctor(doctor); hold.setPatient(patient); hold.setSlotStart(slot(10,0)); hold.setSlotEnd(slot(10,30)); hold.setStatus(SlotHoldStatus.ACTIVE); hold.setExpiresAt(Instant.now().minusSeconds(10)); hold = slotHoldRepository.save(hold);
        mockMvc.perform(post("/api/appointments/"+hold.getId()+"/confirm").header("Authorization","Bearer "+patientToken))
                .andExpect(status().isConflict());
    }
    //19 another patient cannot confirm
    @Test
    void test19_anotherPatientCannotConfirm() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        mockMvc.perform(post("/api/appointments/"+holdId+"/confirm").header("Authorization","Bearer "+patient2Token))
                .andExpect(status().isNotFound());
    }
    //20 already converted cannot be confirmed twice
    @Test
    void test20_alreadyConvertedCannotConfirmTwice() throws Exception {
        UUID holdId = createHoldAndReturnId(patientToken, doctor.getId(), slot(10,0));
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"x\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+holdId+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+holdId+"/confirm").header("Authorization","Bearer "+patientToken))
                .andExpect(status().isConflict());
    }
    //21 patient sees only own
    @Test
    void test21_patientSeesOnlyOwn() throws Exception {
        UUID h1 = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h1+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+h1+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated());
        UUID h2 = createHoldAndReturnId(patient2Token, doctor.getId(), slot(9,30));
        mockMvc.perform(post("/api/appointments/"+h2+"/symptoms").header("Authorization","Bearer "+patient2Token).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"b\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+h2+"/confirm").header("Authorization","Bearer "+patient2Token)).andExpect(status().isCreated());

        MvcResult r = mockMvc.perform(get("/api/patient/appointments").header("Authorization","Bearer "+patientToken)).andExpect(status().isOk()).andReturn();
        JsonNode arr = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(arr.size()).isEqualTo(1);
        assertThat(arr.get(0).get("patientId").asText()).isEqualTo(patient.getId().toString());
    }
    //22 patient cannot access another's appointment
    @Test
    void test22_patientCannotAccessAnotherAppointment() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(get("/api/appointments/"+apptId).header("Authorization","Bearer "+patient2Token))
                .andExpect(status().isForbidden());
    }
    //23 doctor sees only own
    @Test
    void test23_doctorSeesOnlyOwn() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(get("/api/doctor/appointments").header("Authorization","Bearer "+doctorToken)).andExpect(status().isOk()).andReturn();
        JsonNode arr = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(arr.size()).isEqualTo(1);
        // doctor2 should see 0
        MvcResult r2 = mockMvc.perform(get("/api/doctor/appointments").header("Authorization","Bearer "+jwtService.generateToken(doctor2.getUser()))).andExpect(status().isOk()).andReturn();
        JsonNode arr2 = objectMapper.readTree(r2.getResponse().getContentAsString());
        assertThat(arr2.size()).isEqualTo(0);
    }
    //24 doctor cannot access another doctor's appointment
    @Test
    void test24_doctorCannotAccessAnotherDoctorAppointment() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(get("/api/appointments/"+apptId).header("Authorization","Bearer "+jwtService.generateToken(doctor2.getUser())))
                .andExpect(status().isForbidden());
    }
    //25 cancel
    @Test
    void test25_patientCanCancel() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(post("/api/appointments/"+apptId+"/cancel").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"change\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
    //26 cancelled no longer blocks
    @Test
    void test26_cancelledNoLongerBlocks() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(post("/api/appointments/"+apptId+"/cancel").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // Now patient2 should be able to hold same slot
        UUID h2 = createHoldAndReturnId(patient2Token, doctor.getId(), slot(9,0));
        assertThat(h2).isNotNull();
    }
    //27 already cancelled cannot cancel again
    @Test
    void test27_alreadyCancelledCannotCancelAgain() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(post("/api/appointments/"+apptId+"/cancel").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/"+apptId+"/cancel").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }
    //28 valid reschedule
    @Test
    void test28_validRescheduleSucceeds() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        String body = "{\"newSlotStart\":\""+slot(10,0).toString()+"\"}";
        mockMvc.perform(post("/api/appointments/"+apptId+"/reschedule").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentStart").value(slot(10,0).toString()));
    }
    //30 reschedule to occupied returns 409
    @Test
    void test30_rescheduleToOccupiedReturns409() throws Exception {
        UUID h1 = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h1+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r1 = mockMvc.perform(post("/api/appointments/"+h1+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId1 = UUID.fromString(objectMapper.readTree(r1.getResponse().getContentAsString()).get("id").asText());

        UUID h2 = createHoldAndReturnId(patient2Token, doctor.getId(), slot(10,0));
        mockMvc.perform(post("/api/appointments/"+h2+"/symptoms").header("Authorization","Bearer "+patient2Token).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"b\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+h2+"/confirm").header("Authorization","Bearer "+patient2Token)).andExpect(status().isCreated());

        String body = "{\"newSlotStart\":\""+slot(10,0).toString()+"\"}";
        mockMvc.perform(post("/api/appointments/"+apptId1+"/reschedule").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
    //31 failed reschedule leaves original unchanged
    @Test
    void test31_failedRescheduleLeavesOriginal() throws Exception {
        UUID h1 = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h1+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r1 = mockMvc.perform(post("/api/appointments/"+h1+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId1 = UUID.fromString(objectMapper.readTree(r1.getResponse().getContentAsString()).get("id").asText());
        Instant originalStart = slot(9,0);
        // create blocking at 10:00
        UUID h2 = createHoldAndReturnId(patient2Token, doctor.getId(), slot(10,0));
        mockMvc.perform(post("/api/appointments/"+h2+"/symptoms").header("Authorization","Bearer "+patient2Token).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"b\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/appointments/"+h2+"/confirm").header("Authorization","Bearer "+patient2Token)).andExpect(status().isCreated());

        String body = "{\"newSlotStart\":\""+slot(10,0).toString()+"\"}";
        mockMvc.perform(post("/api/appointments/"+apptId1+"/reschedule").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        MvcResult get = mockMvc.perform(get("/api/appointments/"+apptId1).header("Authorization","Bearer "+patientToken)).andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(get.getResponse().getContentAsString());
        assertThat(node.get("appointmentStart").asText()).isEqualTo(originalStart.toString());
        assertThat(node.get("status").asText()).isEqualTo("BOOKED");
    }
    //32 rescheduled blocks new slot
    @Test
    void test32_rescheduledBlocksNewSlot() throws Exception {
        UUID h = createHoldAndReturnId(patientToken, doctor.getId(), slot(9,0));
        mockMvc.perform(post("/api/appointments/"+h+"/symptoms").header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"a\"}")).andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/appointments/"+h+"/confirm").header("Authorization","Bearer "+patientToken)).andExpect(status().isCreated()).andReturn();
        UUID apptId = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        String body = "{\"newSlotStart\":\""+slot(11,0).toString()+"\"}";
        mockMvc.perform(post("/api/appointments/"+apptId+"/reschedule").header("Authorization","Bearer "+patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        // Now new hold at 11:00 should be conflict
        String holdBody = "{\"doctorId\":\""+doctor.getId()+"\",\"slotStart\":\""+slot(11,0).toString()+"\"}";
        mockMvc.perform(post("/api/appointments/hold").header("Authorization","Bearer "+patient2Token)
                .contentType(MediaType.APPLICATION_JSON).content(holdBody))
                .andExpect(status().isConflict());
    }

    private User saveUser(String email, Role role) {
        User u = new User(); u.setEmail(email); u.setPasswordHash(passwordEncoder.encode("Test@123")); u.setFirstName("Test"); u.setLastName("User"); u.setRole(role); return userRepository.save(u);
    }
}
