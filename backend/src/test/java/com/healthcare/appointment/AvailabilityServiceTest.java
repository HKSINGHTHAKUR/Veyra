package com.healthcare.appointment;

import com.healthcare.appointment.dto.AvailabilityResponse;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.repository.*;
import com.healthcare.appointment.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests 12–20: Availability calculation algorithm tested directly via the service.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvailabilityServiceTest {

    @Autowired AvailabilityService availabilityService;

    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired DoctorWorkingHoursRepository workingHoursRepository;
    @Autowired DoctorLeaveRepository leaveRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired SlotHoldRepository slotHoldRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // A known future Monday so slots are never in the past
    private static final LocalDate TEST_DATE = LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
            .plusWeeks(4);

    private Doctor doctor;
    private Patient patient;

    @BeforeEach
    void setUp() {
        User doctorUser = buildUser("avail-doc@test.com", Role.DOCTOR);
        doctor = new Doctor();
        doctor.setUser(doctorUser);
        doctor.setSpecialisation("Test");
        doctor = doctorRepository.save(doctor);

        User patientUser = buildUser("avail-pat@test.com", Role.PATIENT);
        patient = new Patient();
        patient.setUser(patientUser);
        patient = patientRepository.save(patient);
    }

    // ── Test 12: Correct slots generated from working hours ───────────────────

    @Test
    void test12_workingHours_09to11_30min_produces4Slots() {
        addWorkingHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), TEST_DATE);

        // 09:00, 09:30, 10:00, 10:30 → 4 slots
        assertThat(result.getSlots()).hasSize(4);
        assertThat(result.getSlots()).allMatch(s -> s.isAvailable());
    }

    // ── Test 13: BOOKED appointment removes slot ──────────────────────────────

    @Test
    void test13_bookedAppointment_blocksSlot() {
        addWorkingHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        Instant start = TEST_DATE.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant end   = TEST_DATE.atTime(9, 30).toInstant(ZoneOffset.UTC);
        createAppointment(start, end, AppointmentStatus.BOOKED);

        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), TEST_DATE);

        long availableCount = result.getSlots().stream().filter(s -> s.isAvailable()).count();
        assertThat(availableCount).isEqualTo(3); // 4 total − 1 booked

        boolean slot9Available = result.getSlots().stream()
                .anyMatch(s -> s.getSlotStart().equals(start) && s.isAvailable());
        assertThat(slot9Available).isFalse();
    }

    // ── Test 14: RESCHEDULED appointment removes slot ─────────────────────────

    @Test
    void test14_rescheduledAppointment_blocksSlot() {
        addWorkingHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        Instant start = TEST_DATE.atTime(9, 30).toInstant(ZoneOffset.UTC);
        Instant end   = TEST_DATE.atTime(10, 0).toInstant(ZoneOffset.UTC);
        createAppointment(start, end, AppointmentStatus.RESCHEDULED);

        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), TEST_DATE);

        boolean slotBlocked = result.getSlots().stream()
                .anyMatch(s -> s.getSlotStart().equals(start) && !s.isAvailable());
        assertThat(slotBlocked).isTrue();
    }

    // ── Test 15: CANCELLED appointment does NOT block slot ────────────────────

    @Test
    void test15_cancelledAppointment_doesNotBlockSlot() {
        addWorkingHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        Instant start = TEST_DATE.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant end   = TEST_DATE.atTime(9, 30).toInstant(ZoneOffset.UTC);
        createAppointment(start, end, AppointmentStatus.CANCELLED);

        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), TEST_DATE);

        // All 4 slots should still be available
        assertThat(result.getSlots()).hasSize(4);
        assertThat(result.getSlots()).allMatch(s -> s.isAvailable());
    }

    // ── Test 16: ACTIVE hold removes slot ────────────────────────────────────

    @Test
    void test16_activeHold_blocksSlot() {
        addWorkingHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        Instant start = TEST_DATE.atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant end   = TEST_DATE.atTime(10, 30).toInstant(ZoneOffset.UTC);
        createHold(start, end, SlotHoldStatus.ACTIVE, Instant.now().plusSeconds(600));

        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), TEST_DATE);

        boolean heldSlotUnavailable = result.getSlots().stream()
                .anyMatch(s -> s.getSlotStart().equals(start) && !s.isAvailable());
        assertThat(heldSlotUnavailable).isTrue();
    }

    // ── Test 17: EXPIRED hold does NOT block slot ─────────────────────────────

    @Test
    void test17_expiredHold_doesNotBlockSlot() {
        addWorkingHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        Instant start = TEST_DATE.atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant end   = TEST_DATE.atTime(10, 30).toInstant(ZoneOffset.UTC);
        // Hold is EXPIRED — should not affect availability
        createHold(start, end, SlotHoldStatus.EXPIRED, Instant.now().minusSeconds(60));

        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), TEST_DATE);

        boolean slotAvailable = result.getSlots().stream()
                .anyMatch(s -> s.getSlotStart().equals(start) && s.isAvailable());
        assertThat(slotAvailable).isTrue();
    }

    // ── Test 18: Past slots are not returned ──────────────────────────────────

    @Test
    void test18_pastDate_returnsEmptySlots() {
        // Add working hours for the day of week matching yesterday
        LocalDate yesterday = LocalDate.now().minusDays(1);
        addWorkingHours(yesterday.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(17, 0), 30);

        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), yesterday);

        // All slots are in the past — none should be returned
        assertThat(result.getSlots()).isEmpty();
    }

    // ── Test 19: Invalid arbitrary time rejected by slot validation ───────────

    @Test
    void test19_arbitrarySlotStart_isInvalid() {
        addWorkingHours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), 30);

        // 09:07 is not on the 30-minute grid
        Instant invalidStart = TEST_DATE.atTime(9, 7).toInstant(ZoneOffset.UTC);
        Instant invalidEnd   = TEST_DATE.atTime(9, 37).toInstant(ZoneOffset.UTC);

        assertThat(availabilityService.isValidSlot(
                doctor.getId(), TEST_DATE, invalidStart, invalidEnd)).isFalse();

        // 09:00 IS valid
        Instant validStart = TEST_DATE.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant validEnd   = TEST_DATE.atTime(9, 30).toInstant(ZoneOffset.UTC);

        assertThat(availabilityService.isValidSlot(
                doctor.getId(), TEST_DATE, validStart, validEnd)).isTrue();
    }

    // ── Test 20: Doctor with no working hours has no availability ─────────────

    @Test
    void test20_noWorkingHours_availabilityIsEmpty() {
        // No working hours added
        AvailabilityResponse result = availabilityService.getAvailability(doctor.getId(), TEST_DATE);
        assertThat(result.getSlots()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addWorkingHours(DayOfWeek day, LocalTime start, LocalTime end, int duration) {
        DoctorWorkingHours wh = new DoctorWorkingHours();
        wh.setDoctor(doctor);
        wh.setDayOfWeek(day);
        wh.setStartTime(start);
        wh.setEndTime(end);
        wh.setSlotDurationMinutes(duration);
        workingHoursRepository.save(wh);
    }

    private void createAppointment(Instant start, Instant end, AppointmentStatus status) {
        Appointment appt = new Appointment();
        appt.setPatient(patient);
        appt.setDoctor(doctor);
        appt.setAppointmentStart(start);
        appt.setAppointmentEnd(end);
        appt.setStatus(status);
        appointmentRepository.save(appt);
    }

    private void createHold(Instant slotStart, Instant slotEnd,
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
