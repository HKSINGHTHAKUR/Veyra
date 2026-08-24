package com.healthcare.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.integration.calendar.FakeGoogleCalendarClient;
import com.healthcare.appointment.repository.*;
import com.healthcare.appointment.scheduler.CalendarSyncWorker;
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
class GoogleCalendarIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired DoctorWorkingHoursRepository workingHoursRepository;
    @Autowired SlotHoldRepository slotHoldRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired SymptomSubmissionRepository symptomRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CalendarSyncRepository calendarSyncRepository;
    @Autowired CalendarCredentialRepository credentialRepository;
    @Autowired AiSummaryRepository aiSummaryRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionItemRepository prescriptionItemRepository;

    @Autowired FakeGoogleCalendarClient fakeGoogle;
    @Autowired CalendarSyncWorker calendarWorker;

    private User patientUser, doctorUser;
    private Patient patient;
    private Doctor doctor;
    private String patientToken, doctorToken;
    private final LocalDate TEST_DATE = LocalDate.now().plusWeeks(5)
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        fakeGoogle.reset();
        prescriptionItemRepository.deleteAll();
        consultationRepository.deleteAll();
        aiSummaryRepository.deleteAll();
        calendarSyncRepository.deleteAll();
        credentialRepository.deleteAll();
        notificationRepository.deleteAll();
        symptomRepository.deleteAll();
        slotHoldRepository.deleteAll();
        appointmentRepository.deleteAll();
        workingHoursRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        String suf = UUID.randomUUID().toString().substring(0, 6);
        User du = saveUser("doc-g-" + suf + "@test.com", Role.DOCTOR);
        doctor = new Doctor(); doctor.setUser(du); doctor.setSpecialisation("Cardiology"); doctor = doctorRepository.save(doctor);
        doctorUser = du; doctorToken = jwtService.generateToken(du);

        DoctorWorkingHours wh = new DoctorWorkingHours();
        wh.setDoctor(doctor); wh.setDayOfWeek(TEST_DATE.getDayOfWeek());
        wh.setStartTime(LocalTime.of(9, 0)); wh.setEndTime(LocalTime.of(17, 0)); wh.setSlotDurationMinutes(30);
        workingHoursRepository.save(wh);

        patientUser = saveUser("pat-g-" + suf + "@test.com", Role.PATIENT);
        patient = new Patient(); patient.setUser(patientUser); patient = patientRepository.save(patient);
        patientToken = jwtService.generateToken(patientUser);
    }

    private Instant slot(int h) { return TEST_DATE.atTime(h, 0).toInstant(ZoneOffset.UTC); }

    /** Connects the given user's Google Calendar via the real OAuth endpoints (fake exchange).
     *  The browser-style callback is now PUBLIC — it authenticates via the single-use state,
     *  so NO Authorization header is sent on the callback leg. */
    private void connect(User user, String token) throws Exception {
        MvcResult connectRes = mockMvc.perform(get("/api/calendar/connect")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").isNotEmpty())
                .andReturn();
        String url = objectMapper.readTree(connectRes.getResponse().getContentAsString())
                .get("authorizationUrl").asText();
        assertThat(url).contains("client_id=").contains("scope=").contains("state=");
        String state = url.substring(url.indexOf("state=") + 6);
        // state is a server-generated random hex value, NOT the userId
        assertThat(state).isNotEqualTo(user.getId().toString());

        // Browser-style callback: NO Authorization header; success is a 302 to the frontend
        MvcResult cb = mockMvc.perform(get("/api/calendar/callback")
                        .param("code", "test-auth-code")
                        .param("state", state))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/calendar/connected")))
                .andReturn();
        String location = cb.getResponse().getHeader("Location");
        assertThat(location).contains("status=success");

        assertThat(credentialRepository.existsByUserId(user.getId())).isTrue();
    }

    private String holdAndConfirm(String token) throws Exception {
        String holdBody = "{\"doctorId\":\"" + doctor.getId() + "\",\"slotStart\":\"" + slot(10) + "\"}";
        String holdId = objectMapper.readTree(mockMvc.perform(post("/api/appointments/hold")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("holdId").asText();
        mockMvc.perform(post("/api/appointments/" + holdId + "/symptoms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"symptoms\":\"headache\"}"))
                .andExpect(status().isCreated());
        return objectMapper.readTree(mockMvc.perform(post("/api/appointments/" + holdId + "/confirm")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    // ── OAuth: 1–3 ──────────────────────────────────────────────────────────

    @Test
    void oauth01_authorizationUrlGeneratedCorrectly() throws Exception {
        connect(patientUser, patientToken);
    }

    @Test
    void oauth02_callbackStoresCredentials_notExposedViaApi() throws Exception {
        connect(patientUser, patientToken);

        // Status endpoint exposes only a boolean, never tokens
        MvcResult res = mockMvc.perform(get("/api/calendar/status")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk()).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(body).doesNotContain("fake-access").doesNotContain("fake-refresh");

        // Credential row itself holds tokens internally but API responses never leak them
        var cred = credentialRepository.findByUserId(patientUser.getId()).orElseThrow();
        assertThat(cred.getRefreshToken()).startsWith("fake-refresh");
        assertThat(body.contains(cred.getRefreshToken())).isFalse();
    }

    @Test
    void oauth03_unknownOrReplayedStateRejected() throws Exception {
        mockMvc.perform(get("/api/calendar/connect").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        // Unknown state (never issued / already consumed) -> 400, no credential created
        mockMvc.perform(get("/api/calendar/callback")
                        .param("code", "some-code")
                        .param("state", "deadbeef".repeat(8)))
                .andExpect(status().isBadRequest());
        assertThat(credentialRepository.existsByUserId(patientUser.getId())).isFalse();

        // Single-use: a valid state consumed once must fail on replay
        MvcResult connectRes = mockMvc.perform(get("/api/calendar/connect")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk()).andReturn();
        String url = objectMapper.readTree(connectRes.getResponse().getContentAsString())
                .get("authorizationUrl").asText();
        String state = url.substring(url.indexOf("state=") + 6);

        mockMvc.perform(get("/api/calendar/callback")
                        .param("code", "test-auth-code").param("state", state))
                .andExpect(status().is3xxRedirection());
        assertThat(credentialRepository.existsByUserId(patientUser.getId())).isTrue();

        mockMvc.perform(get("/api/calendar/callback")
                        .param("code", "test-auth-code").param("state", state))
                .andExpect(status().isBadRequest()); // replay rejected
    }

    // ── Create: 4–6 (+ privacy check on event content) ──────────────────────

    @Test
    void create04_05_06_bookedCreatesPending_syncedAndEventStored() throws Exception {
        connect(patientUser, patientToken);
        String apptId = holdAndConfirm(patientToken);

        // PENDING sync created at confirmation time
        var syncOpt = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId());
        assertThat(syncOpt).isPresent();
        assertThat(syncOpt.get().getStatus()).isEqualTo(CalendarSyncStatus.PENDING);

        // Worker creates event
        calendarWorker.processForTest();
        CalendarSync synced = calendarSyncRepository.findById(syncOpt.get().getId()).orElseThrow();
        assertThat(synced.getStatus()).isEqualTo(CalendarSyncStatus.SYNCED);
        assertThat(synced.getGoogleEventId()).isNotNull();

        // Event stored in fake calendar exactly once, with scheduling facts only
        assertThat(fakeGoogle.getEvents()).containsKey(synced.getGoogleEventId());
        assertThat(fakeGoogle.getCreateCount()).isEqualTo(1);
        var details = fakeGoogle.getEvents().get(synced.getGoogleEventId());
        assertThat(details.startUtc()).isEqualTo(slot(10));
        assertThat(details.title()).contains(doctor.getSpecialisation());
        // Privacy: no symptoms / clinical data in event content
        assertThat(details.description()).doesNotContain("headache");
    }

    // ── Failure: 7–8 ────────────────────────────────────────────────────────

    @Test
    void failure07_08_createFailure_failed_appointmentRemainsBooked() throws Exception {
        connect(patientUser, patientToken);
        fakeGoogle.setFailCreate(true);
        String apptId = holdAndConfirm(patientToken);

        calendarWorker.processForTest();
        var sync = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(sync.getStatus()).isEqualTo(CalendarSyncStatus.FAILED);
        assertThat(sync.getErrorMessage()).isNotBlank();

        Appointment appt = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.BOOKED); // isolation
        fakeGoogle.setFailCreate(false);
    }

    // ── Reschedule: 9–11 ────────────────────────────────────────────────────

    @Test
    void reschedule09_10_rescheduleReenqueues_workerUpdatesExistingEvent() throws Exception {
        connect(patientUser, patientToken);
        String apptId = holdAndConfirm(patientToken);
        calendarWorker.processForTest(); // create

        var syncBefore = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        String eventId = syncBefore.getGoogleEventId();
        int createsAfterFirstRun = fakeGoogle.getCreateCount();

        // Reschedule to 11:00
        String body = "{\"newSlotStart\":\"" + slot(11) + "\"}";
        mockMvc.perform(post("/api/appointments/" + apptId + "/reschedule")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Sync re-enqueued as PENDING
        var requeued = calendarSyncRepository.findById(syncBefore.getId()).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(CalendarSyncStatus.PENDING);

        calendarWorker.processForTest();
        var after = calendarSyncRepository.findById(syncBefore.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(CalendarSyncStatus.SYNCED);
        assertThat(after.getGoogleEventId()).isEqualTo(eventId);      // same event updated…
        assertThat(fakeGoogle.getUpdateCount()).isEqualTo(1);          // …not recreated
        assertThat(fakeGoogle.getCreateCount()).isEqualTo(createsAfterFirstRun);
        assertThat(fakeGoogle.getEvents().get(eventId).startUtc()).isEqualTo(slot(11)); // new time

        Appointment appt = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(appt.getAppointmentStart()).isEqualTo(slot(11));
    }

    @Test
    void reschedule11_updateFailureDoesNotUndoReschedule() throws Exception {
        connect(patientUser, patientToken);
        String apptId = holdAndConfirm(patientToken);
        calendarWorker.processForTest();

        fakeGoogle.setFailUpdate(true);
        String body = "{\"newSlotStart\":\"" + slot(11) + "\"}";
        mockMvc.perform(post("/api/appointments/" + apptId + "/reschedule")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        calendarWorker.processForTest();

        var sync = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(sync.getStatus()).isEqualTo(CalendarSyncStatus.FAILED);

        // Appointment remains rescheduled despite Google failure
        Appointment appt = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(appt.getAppointmentStart()).isEqualTo(slot(11));
        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        fakeGoogle.setFailUpdate(false);
    }

    // ── Cancellation: 12–14 ─────────────────────────────────────────────────

    @Test
    void cancel12_13_cancellationDeletesEvent_viaWorker() throws Exception {
        connect(patientUser, patientToken);
        String apptId = holdAndConfirm(patientToken);
        calendarWorker.processForTest();
        String eventId = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow().getGoogleEventId();

        mockMvc.perform(post("/api/appointments/" + apptId + "/cancel")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // Re-enqueued as PENDING for deletion
        var pending = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(CalendarSyncStatus.PENDING);

        calendarWorker.processForTest();
        var done = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(CalendarSyncStatus.CANCELLED);
        assertThat(fakeGoogle.getDeletedEventIds()).contains(eventId);
        assertThat(fakeGoogle.getEvents()).doesNotContainKey(eventId);

        Appointment appt = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel14_deleteFailureDoesNotUndoCancellation() throws Exception {
        connect(patientUser, patientToken);
        String apptId = holdAndConfirm(patientToken);
        calendarWorker.processForTest();

        fakeGoogle.setFailDelete(true);
        mockMvc.perform(post("/api/appointments/" + apptId + "/cancel")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        calendarWorker.processForTest();

        var sync = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(sync.getStatus()).isEqualTo(CalendarSyncStatus.FAILED);

        Appointment appt = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.CANCELLED); // not resurrected
        fakeGoogle.setFailDelete(false);
    }

    // ── Idempotency: 15–16 ──────────────────────────────────────────────────

    @Test
    void idempotency15_16_syncedNotRecreated_existingEventUpdated() throws Exception {
        connect(patientUser, patientToken);
        String apptId = holdAndConfirm(patientToken);
        calendarWorker.processForTest();

        int creates = fakeGoogle.getCreateCount();
        calendarWorker.processForTest(); // second run – nothing to do
        calendarWorker.processForTest(); // third run
        assertThat(fakeGoogle.getCreateCount()).isEqualTo(creates); // no duplicate events
        assertThat(calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow().getStatus())
                .isEqualTo(CalendarSyncStatus.SYNCED);
    }

    // ── Retry: 17–20 ────────────────────────────────────────────────────────

    @Test
    void retry17_18_20_failedRetries_attemptIncrements_eventualSynced() throws Exception {
        connect(patientUser, patientToken);
        fakeGoogle.setFailCreate(true);
        String apptId = holdAndConfirm(patientToken);
        calendarWorker.processForTest();

        var sync = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(sync.getStatus()).isEqualTo(CalendarSyncStatus.FAILED);
        int attemptsAfterFirst = sync.getAttemptCount();
        assertThat(attemptsAfterFirst).isGreaterThanOrEqualTo(1);

        // Simulate backoff elapsed so the sync is retry-eligible immediately
        sync.setLastSyncAttemptAt(Instant.now().minusSeconds(3600));
        calendarSyncRepository.save(sync);
        fakeGoogle.setFailCreate(false);

        calendarWorker.processForTest();
        var retried = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(CalendarSyncStatus.SYNCED);
        assertThat(retried.getAttemptCount()).isGreaterThan(attemptsAfterFirst);
        assertThat(retried.getGoogleEventId()).isNotNull();
    }

    @Test
    void retry19_maxRetriesPreventsInfiniteAttempts() throws Exception {
        connect(patientUser, patientToken);
        fakeGoogle.setFailCreate(true);
        String apptId = holdAndConfirm(patientToken);

        // Force attempt count to max (3)
        var sync = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        sync.setAttemptCount(3);
        sync.setStatus(CalendarSyncStatus.FAILED);
        sync.setLastSyncAttemptAt(Instant.now().minusSeconds(7200)); // backoff long elapsed
        calendarSyncRepository.save(sync);

        calendarWorker.processForTest();
        var stillFailed = calendarSyncRepository.findByAppointmentIdAndUserId(
                UUID.fromString(apptId), patientUser.getId()).orElseThrow();
        assertThat(stillFailed.getStatus()).isEqualTo(CalendarSyncStatus.FAILED);
        assertThat(stillFailed.getAttemptCount()).isEqualTo(3); // no further attempt
    }

    // ── Ownership / unconnected users ───────────────────────────────────────

    @Test
    void ownership_callbackWithoutJwtStillSecure_unknownStateRejected() throws Exception {
        // The callback is intentionally public (browser redirect has no JWT) but an
        // unknown/absent state can never authenticate anyone -> 400, no credential.
        mockMvc.perform(get("/api/calendar/callback")
                        .param("code", "x").param("state", "ffffffff".repeat(8)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/calendar/callback").param("code", "x"))
                .andExpect(status().isBadRequest());
        assertThat(credentialRepository.count()).isEqualTo(0);
    }

    @Test
    void bookingWithoutConnection_noSyncRows_noGoogleCalls() throws Exception {
        // Nobody connected – confirm must still work and create zero sync rows
        String apptId = holdAndConfirm(patientToken);
        assertThat(calendarSyncRepository.findByAppointmentId(UUID.fromString(apptId))).isEmpty();
        calendarWorker.processForTest();
        assertThat(fakeGoogle.getCreateCount()).isEqualTo(0);
        Appointment appt = appointmentRepository.findById(UUID.fromString(apptId)).orElseThrow();
        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    private User saveUser(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@123"));
        u.setFirstName("Test"); u.setLastName("User"); u.setRole(role);
        return userRepository.save(u);
    }
}
