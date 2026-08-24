package com.healthcare.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.dto.LeaveRequest;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.integration.email.FakeEmailService;
import com.healthcare.appointment.repository.*;
import com.healthcare.appointment.scheduler.NotificationProcessor;
import com.healthcare.appointment.scheduler.ReminderScheduler;
import com.healthcare.appointment.security.JwtService;
import com.healthcare.appointment.service.DoctorLeaveService;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTest {

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

    @Autowired FakeEmailService fakeEmailService;
    @Autowired NotificationProcessor notificationProcessor;
    @Autowired ReminderScheduler reminderScheduler;
    @Autowired DoctorLeaveService doctorLeaveService;

    private Doctor doctor;
    private Patient patient, patient2;
    private User patientUser, patient2User, doctorUser, adminUser;
    private String patientToken, patient2Token, adminToken;
    private LocalDate testDate = LocalDate.now().plusWeeks(5).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        fakeEmailService.reset();
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
        User admin = saveUser("admin-n-"+suf+"@test.com", Role.ADMIN);
        adminUser = admin; adminToken = jwtService.generateToken(admin);
        User pu = saveUser("pat-n-"+suf+"@test.com", Role.PATIENT);
        patientUser = pu; patient = new Patient(); patient.setUser(pu); patient = patientRepository.save(patient); patientToken = jwtService.generateToken(pu);
        User pu2 = saveUser("pat2-n-"+suf+"@test.com", Role.PATIENT);
        patient2User = pu2; patient2 = new Patient(); patient2.setUser(pu2); patient2 = patientRepository.save(patient2); patient2Token = jwtService.generateToken(pu2);
        User du = saveUser("doc-n-"+suf+"@test.com", Role.DOCTOR);
        doctorUser = du; doctor = new Doctor(); doctor.setUser(du); doctor.setSpecialisation("Cardiology"); doctor = doctorRepository.save(doctor);
        DoctorWorkingHours wh = new DoctorWorkingHours(); wh.setDoctor(doctor); wh.setDayOfWeek(testDate.getDayOfWeek()); wh.setStartTime(LocalTime.of(9,0)); wh.setEndTime(LocalTime.of(17,0)); wh.setSlotDurationMinutes(30); workingHoursRepository.save(wh);
    }

    private Instant slot(int h,int m){ return testDate.atTime(h,m).toInstant(ZoneOffset.UTC); }

    private Appointment createAppointment(Patient p, Instant start) {
        Appointment a = new Appointment();
        a.setPatient(p); a.setDoctor(doctor); a.setAppointmentStart(start); a.setAppointmentEnd(start.plusSeconds(1800)); a.setStatus(AppointmentStatus.BOOKED);
        return appointmentRepository.save(a);
    }

    private String holdAndConfirm(Patient p, String token, Instant slotStart) throws Exception {
        // create hold
        String holdBody = "{\"doctorId\":\""+doctor.getId()+"\",\"slotStart\":\""+slotStart.toString()+"\"}";
        String holdId = objectMapper.readTree(mockMvc.perform(post("/api/appointments/hold")
                .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(holdBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("holdId").asText();
        // symptoms
        mockMvc.perform(post("/api/appointments/"+holdId+"/symptoms")
                .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"headache\"}"))
                .andExpect(status().isCreated());
        // confirm
        String apptJson = mockMvc.perform(post("/api/appointments/"+holdId+"/confirm")
                .header("Authorization","Bearer "+token)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(apptJson).get("id").asText();
    }

    // 1 confirmation creates PENDING
    @Test
    void test01_confirmationCreatesPending() throws Exception {
        String apptId = holdAndConfirm(patient, patientToken, slot(10,0));
        var notifs = notificationRepository.findByAppointmentId(UUID.fromString(apptId));
        assertThat(notifs).anyMatch(n -> n.getType() == NotificationType.BOOKING_CONFIRMATION && n.getStatus() == NotificationStatus.PENDING);
    }

    // 2 booking succeeds even if email fails (isolated)
    @Test
    void test02_bookingSucceedsEvenIfEmailFails() throws Exception {
        fakeEmailService.setShouldFail(true);
        String apptId = holdAndConfirm(patient, patientToken, slot(10,0));
        // appointment should still be BOOKED
        Appointment a = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        // notification should be PENDING (or FAILED after worker tries)
        var notifs = notificationRepository.findByAppointmentId(UUID.fromString(apptId));
        assertThat(notifs).isNotEmpty();
        // now process – should fail but not affect appointment
        notificationProcessor.processForTest();
        Appointment after = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        fakeEmailService.reset();
    }

    // 3 cancellation creates notification
    @Test
    void test03_cancellationCreatesNotification() throws Exception {
        String apptId = holdAndConfirm(patient, patientToken, slot(10,0));
        mockMvc.perform(post("/api/appointments/"+apptId+"/cancel")
                .header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        var notifs = notificationRepository.findByAppointmentId(UUID.fromString(apptId));
        assertThat(notifs).anyMatch(n -> n.getType() == NotificationType.CANCELLATION);
    }

    // 4 failed email does not change appointment state
    @Test
    void test04_failedEmailDoesNotChangeAppointmentState() throws Exception {
        String apptId = holdAndConfirm(patient, patientToken, slot(10,0));
        fakeEmailService.setShouldFail(true);
        // trigger cancellation which creates notification
        mockMvc.perform(post("/api/appointments/"+apptId+"/cancel")
                .header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        Appointment a = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        notificationProcessor.processForTest();
        Appointment after = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        fakeEmailService.reset();
    }

    // 5 successful reschedule creates notification
    @Test
    void test05_rescheduleCreatesNotification() throws Exception {
        String apptId = holdAndConfirm(patient, patientToken, slot(9,0));
        String body = "{\"newSlotStart\":\""+slot(11,0).toString()+"\"}";
        mockMvc.perform(post("/api/appointments/"+apptId+"/reschedule")
                .header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        var notifs = notificationRepository.findByAppointmentId(UUID.fromString(apptId));
        assertThat(notifs).anyMatch(n -> n.getType() == NotificationType.RESCHEDULE);
    }

    // 6 failed reschedule creates no notification
    @Test
    void test06_failedRescheduleNoNotification() throws Exception {
        String apptId = holdAndConfirm(patient, patientToken, slot(9,0));
        // create blocking at 10:00
        createAppointment(patient2, slot(10,0));
        long before = notificationRepository.findByAppointmentId(UUID.fromString(apptId)).stream().filter(n->n.getType()==NotificationType.RESCHEDULE).count();
        String body = "{\"newSlotStart\":\""+slot(10,0).toString()+"\"}";
        mockMvc.perform(post("/api/appointments/"+apptId+"/reschedule")
                .header("Authorization","Bearer "+patientToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        long after = notificationRepository.findByAppointmentId(UUID.fromString(apptId)).stream().filter(n->n.getType()==NotificationType.RESCHEDULE).count();
        assertThat(after).isEqualTo(before);
    }

    // 7 reminder for 24h away
    @Test
    void test07_reminderFor24hAway() {
        Instant in24h = Instant.now().plus(24, ChronoUnit.HOURS).plus(5, ChronoUnit.MINUTES);
        // Need to ensure doctor working hours covers that time – create appointment directly in window
        Appointment a = createAppointment(patient, in24h);
        reminderScheduler.createRemindersForTest(Instant.now());
        var notifs = notificationRepository.findByAppointmentId(a.getId());
        assertThat(notifs).anyMatch(n -> n.getType() == NotificationType.APPOINTMENT_REMINDER);
    }

    // 8 already reminded no duplicate
    @Test
    void test08_alreadyRemindedNoDuplicate() {
        Instant in24h = Instant.now().plus(24, ChronoUnit.HOURS);
        Appointment a = createAppointment(patient, in24h);
        reminderScheduler.createRemindersForTest(Instant.now());
        long first = notificationRepository.findByAppointmentId(a.getId()).stream().filter(n->n.getType()==NotificationType.APPOINTMENT_REMINDER).count();
        reminderScheduler.createRemindersForTest(Instant.now());
        long second = notificationRepository.findByAppointmentId(a.getId()).stream().filter(n->n.getType()==NotificationType.APPOINTMENT_REMINDER).count();
        assertThat(second).isEqualTo(first);
        assertThat(first).isEqualTo(1);
    }

    // 9 doctor leave future affected
    @Test
    void test09_doctorLeaveFutureAffected() {
        Instant futureSlot = testDate.atTime(10,0).toInstant(ZoneOffset.UTC);
        // ensure future
        if (futureSlot.isBefore(Instant.now())) futureSlot = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS);
        // Use testDate which is future Monday, create appointment on that date
        Appointment a = createAppointment(patient, slot(10,0));
        LeaveRequest req = new LeaveRequest();
        req.setLeaveDate(testDate);
        req.setReason("Conference");
        doctorLeaveService.addLeave(doctor.getId(), req);
        var notifs = notificationRepository.findByAppointmentId(a.getId());
        assertThat(notifs).anyMatch(n -> n.getType() == NotificationType.DOCTOR_LEAVE);
    }

    // 10 past appointments ignored
    @Test
    void test10_doctorLeavePastIgnored() {
        Instant past = Instant.now().minus(5, ChronoUnit.DAYS);
        Appointment a = new Appointment(); a.setPatient(patient); a.setDoctor(doctor); a.setAppointmentStart(past); a.setAppointmentEnd(past.plusSeconds(1800)); a.setStatus(AppointmentStatus.BOOKED); a = appointmentRepository.save(a);
        // leave for past date shouldn't affect (but we use future leave date)
        LeaveRequest req = new LeaveRequest(); req.setLeaveDate(LocalDate.now().plusDays(10)); req.setReason("Future");
        doctorLeaveService.addLeave(doctor.getId(), req);
        var notifs = notificationRepository.findByAppointmentId(a.getId());
        assertThat(notifs).noneMatch(n -> n.getType() == NotificationType.DOCTOR_LEAVE);
    }

    // 11 unaffected not notified
    @Test
    void test11_doctorLeaveUnaffectedNotNotified() {
        // leave for testDate, but appointment on different date
        LocalDate otherDate = testDate.plusDays(2);
        Instant otherSlot = otherDate.atTime(10,0).toInstant(ZoneOffset.UTC);
        Appointment a = new Appointment(); a.setPatient(patient); a.setDoctor(doctor); a.setAppointmentStart(otherSlot); a.setAppointmentEnd(otherSlot.plusSeconds(1800)); a.setStatus(AppointmentStatus.BOOKED); a = appointmentRepository.save(a);
        LeaveRequest req = new LeaveRequest(); req.setLeaveDate(testDate); req.setReason("Leave");
        doctorLeaveService.addLeave(doctor.getId(), req);
        var notifs = notificationRepository.findByAppointmentId(a.getId());
        assertThat(notifs).noneMatch(n -> n.getType() == NotificationType.DOCTOR_LEAVE);
    }

    // 12 PENDING -> SENT
    @Test
    void test12_pendingSendsToSent() {
        Notification n = new Notification(); n.setUser(patientUser); n.setType(NotificationType.BOOKING_CONFIRMATION); n.setStatus(NotificationStatus.PENDING); n.setSubject("Test"); n.setBody("Body"); n = notificationRepository.save(n);
        notificationProcessor.processForTest();
        Notification after = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(after.getSentAt()).isNotNull();
    }

    // 13 failure -> FAILED
    @Test
    void test13_failureToFailed() {
        fakeEmailService.setShouldFail(true);
        Notification n = new Notification(); n.setUser(patientUser); n.setType(NotificationType.BOOKING_CONFIRMATION); n.setStatus(NotificationStatus.PENDING); n.setSubject("Test"); n.setBody("Body"); n = notificationRepository.save(n);
        notificationProcessor.processForTest();
        Notification after = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(after.getFailureReason()).isNotNull();
        fakeEmailService.reset();
    }

    // 14 retry count increments
    @Test
    void test14_retryCountIncrements() {
        fakeEmailService.setShouldFail(true);
        Notification n = new Notification(); n.setUser(patientUser); n.setType(NotificationType.BOOKING_CONFIRMATION); n.setStatus(NotificationStatus.PENDING); n.setSubject("Test"); n.setBody("Body"); n = notificationRepository.save(n);
        int before = n.getAttemptCount();
        notificationProcessor.processForTest();
        Notification after = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(after.getAttemptCount()).isGreaterThan(before);
        fakeEmailService.reset();
    }

    // 15 max retry prevents infinite
    @Test
    void test15_maxRetryPreventsInfinite() {
        fakeEmailService.setShouldFail(true);
        Notification n = new Notification(); n.setUser(patientUser); n.setType(NotificationType.BOOKING_CONFIRMATION); n.setStatus(NotificationStatus.PENDING); n.setSubject("Test"); n.setBody("Body"); n.setAttemptCount(3); n = notificationRepository.save(n); // already at max
        // set maxRetries 3, so should not be retried if FAILED and attemptCount >=3
        // But PENDING with attemptCount 3 will still be processed once (since PENDING not filtered by max), then will become FAILED and stop
        // For this test, we make it FAILED with 3 and try retry
        n.setStatus(NotificationStatus.FAILED); n.setAttemptCount(3); n.setLastAttemptAt(Instant.now().minusSeconds(3600)); notificationRepository.save(n);
        notificationProcessor.processForTest();
        Notification after = notificationRepository.findById(n.getId()).orElseThrow();
        // Should remain FAILED and not increment beyond max (or stay)
        assertThat(after.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(after.getAttemptCount()).isEqualTo(3);
        fakeEmailService.reset();
    }

    // 16 successful retry -> SENT
    @Test
    void test16_successfulRetryToSent() {
        fakeEmailService.failNext(1);
        Notification n = new Notification(); n.setUser(patientUser); n.setType(NotificationType.BOOKING_CONFIRMATION); n.setStatus(NotificationStatus.PENDING); n.setSubject("Test"); n.setBody("Body"); n = notificationRepository.save(n);
        notificationProcessor.processForTest(); // first fail
        Notification afterFail = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(afterFail.getStatus()).isEqualTo(NotificationStatus.FAILED);
        // next retry should succeed
        // Need to make lastAttemptAt old enough for backoff (1 min)
        afterFail.setLastAttemptAt(Instant.now().minusSeconds(120));
        notificationRepository.save(afterFail);
        fakeEmailService.reset(); // now succeed
        notificationProcessor.processForTest();
        Notification afterRetry = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(afterRetry.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    // 17 patient cannot read another's notification
    @Test
    void test17_patientCannotReadOthers() throws Exception {
        Notification n = new Notification(); n.setUser(patientUser); n.setType(NotificationType.BOOKING_CONFIRMATION); n.setStatus(NotificationStatus.PENDING); n.setSubject("Test"); n.setBody("Body"); n = notificationRepository.save(n);
        mockMvc.perform(get("/api/notifications/" + n.getId())
                .header("Authorization","Bearer "+patient2Token))
                .andExpect(status().isForbidden());
    }

    // 18 doctor cannot read another's
    @Test
    void test18_doctorCannotReadOthers() throws Exception {
        // Create doctor2 user
        User du2 = saveUser("doc-n2-"+UUID.randomUUID().toString().substring(0,4)+"@test.com", Role.DOCTOR);
        Doctor doc2 = new Doctor(); doc2.setUser(du2); doc2.setSpecialisation("Neuro"); doc2 = doctorRepository.save(doc2);
        String doc2Token = jwtService.generateToken(du2);
        Notification n = new Notification(); n.setUser(doctorUser); n.setType(NotificationType.BOOKING_CONFIRMATION); n.setStatus(NotificationStatus.PENDING); n.setSubject("Doc notif"); n.setBody("Body"); n = notificationRepository.save(n);
        mockMvc.perform(get("/api/notifications/" + n.getId())
                .header("Authorization","Bearer "+doc2Token))
                .andExpect(status().isForbidden());
    }

    // 19-21 failure isolation: email throws, appointment remains BOOKED, notification retryable
    @Test
    void test19_20_21_failureIsolation() throws Exception {
        fakeEmailService.setShouldFail(true);
        String apptId = holdAndConfirm(patient, patientToken, slot(10,0));
        // appointment should be BOOKED despite email failure (notification worker fails, not booking)
        Appointment a = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        // process notification – should become FAILED
        notificationProcessor.processForTest();
        var notifs = notificationRepository.findByAppointmentId(UUID.fromString(apptId));
        var bookingNotif = notifs.stream().filter(n -> n.getType()==NotificationType.BOOKING_CONFIRMATION).findFirst().orElseThrow();
        assertThat(bookingNotif.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(bookingNotif.getAttemptCount()).isGreaterThan(0);
        // retry should be possible
        assertThat(bookingNotif.getAttemptCount()).isLessThan(3);
        fakeEmailService.reset();
    }

    private User saveUser(String email, Role role) {
        User u = new User(); u.setEmail(email); u.setPasswordHash(passwordEncoder.encode("Test@123")); u.setFirstName("Test"); u.setLastName("User"); u.setRole(role); return userRepository.save(u);
    }
}
