package com.healthcare.appointment.service;

import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.integration.calendar.CalendarException;
import com.healthcare.appointment.integration.calendar.GoogleCalendarClient;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.CalendarCredentialRepository;
import com.healthcare.appointment.repository.CalendarSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates per-appointment Google Calendar synchronisation.
 *
 * Business services only call {@link #enqueueForParticipants(Appointment)} inside their
 * DB transaction – this touches ONLY local rows (no Google calls). Actual API calls happen
 * later in the background worker, so Google outages can never break booking, cancellation
 * or rescheduling (same reliability principle as email/AI).
 *
 * Operation is derived purely from database state (idempotency):
 *   PENDING/FAILED + appointment CANCELLED + eventId exists -> delete event -> CANCELLED
 *   PENDING/FAILED + appointment CANCELLED + no eventId     -> nothing to do -> CANCELLED
 *   PENDING/FAILED + no eventId                             -> create        -> SYNCED
 *   PENDING/FAILED + eventId exists                         -> update        -> SYNCED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private final CalendarSyncRepository calendarSyncRepository;
    private final CalendarCredentialRepository credentialRepository;
    private final CalendarService calendarService;
    private final GoogleCalendarClient googleClient;

    @Value("${app.google.calendar.max-retries:3}")
    private int maxRetries;

    @Value("${app.google.calendar.worker-batch-size:20}")
    private int batchSize;

    // ── Enqueue (DB only – safe inside business transactions) ─────────────

    /**
     * Ensures a CalendarSync row exists for every participant who has connected their
     * Google Calendar, marking it PENDING for (re)processing.
     * Idempotent via uq_cal_appointment_user – repeated calls reuse the same row.
     */
    @Transactional
    public void enqueueForParticipants(Appointment appointment) {
        enqueue(appointment.getPatient().getUser(), appointment);
        enqueue(appointment.getDoctor().getUser(), appointment);
    }

    private void enqueue(User user, Appointment appointment) {
        if (!credentialRepository.existsByUserId(user.getId())) {
            return; // user never connected their calendar – nothing to sync
        }
        Optional<CalendarSync> existing =
                calendarSyncRepository.findByAppointmentIdAndUserId(appointment.getId(), user.getId());
        CalendarSync sync = existing.orElseGet(() -> {
            CalendarSync s = new CalendarSync();
            s.setAppointment(appointment);
            s.setUser(user);
            return s;
        });
        sync.setStatus(CalendarSyncStatus.PENDING);
        sync.setErrorMessage(null);
        calendarSyncRepository.save(sync);
    }

    // ── Background processing ─────────────────────────────────────────────

    private long backoffMinutes(int attemptCount) {
        if (attemptCount <= 1) return 1;
        if (attemptCount == 2) return 5;
        return 15;
    }

    @Transactional
    public void processPending() {
        List<CalendarSync> pending = calendarSyncRepository.findByStatus(CalendarSyncStatus.PENDING);
        pending.stream().limit(batchSize).forEach(this::tryProcess);
        calendarSyncRepository.findByStatus(CalendarSyncStatus.FAILED).stream()
                .filter(s -> s.getAttemptCount() < maxRetries && isEligibleForRetry(s))
                .limit(batchSize)
                .forEach(this::tryProcess);
    }

    /** Deterministic entry point for tests. */
    @Transactional
    public void processForTest() {
        processPending();
    }

    private boolean isEligibleForRetry(CalendarSync s) {
        if (s.getLastSyncAttemptAt() == null) return true;
        return Instant.now().isAfter(
                s.getLastSyncAttemptAt().plusSeconds(backoffMinutes(s.getAttemptCount()) * 60));
    }

    private void tryProcess(CalendarSync incoming) {
        CalendarSync cs = calendarSyncRepository.findById(incoming.getId()).orElse(null);
        if (cs == null || cs.getStatus() == CalendarSyncStatus.SYNCED
                || cs.getStatus() == CalendarSyncStatus.CANCELLED) {
            log.debug("CalendarSync {} already finished or claimed", incoming.getId());
            return;
        }
        if (cs.getStatus() == CalendarSyncStatus.FAILED && cs.getAttemptCount() >= maxRetries) {
            return; // permanently failed
        }
        // Claim: bump attempt counter and timestamp before any external call
        cs.setAttemptCount(cs.getAttemptCount() + 1);
        cs.setLastSyncAttemptAt(Instant.now());
        cs.setStatus(CalendarSyncStatus.PENDING); // remains claimable until outcome set
        cs = calendarSyncRepository.saveAndFlush(cs);

        Appointment appt = cs.getAppointment();
        try {
            String accessToken = calendarService.getValidAccessToken(cs.getUser());
            if (accessToken == null) {
                finish(cs, CalendarSyncStatus.CANCELLED, "User disconnected calendar");
                return;
            }

            boolean cancelledAppointment = appt.getStatus() == AppointmentStatus.CANCELLED;

            if (cancelledAppointment && cs.getGoogleEventId() != null) {
                googleClient.deleteEvent(accessToken, cs.getGoogleEventId());
                finish(cs, CalendarSyncStatus.CANCELLED, null);
                log.info("Deleted Google event {} for appointment {}", cs.getGoogleEventId(), appt.getId());

            } else if (cancelledAppointment) {
                finish(cs, CalendarSyncStatus.CANCELLED, null); // no event ever created

            } else if (cs.getGoogleEventId() == null) {
                String eventId = googleClient.createEvent(accessToken,
                        buildDetails(appt, cs.getUser()));
                cs.setGoogleEventId(eventId);
                finish(cs, CalendarSyncStatus.SYNCED, null);
                log.info("Created Google event {} for appointment {}", eventId, appt.getId());

            } else {
                googleClient.updateEvent(accessToken, cs.getGoogleEventId(),
                        buildDetails(appt, cs.getUser()));
                finish(cs, CalendarSyncStatus.SYNCED, null);
                log.info("Updated Google event {} for appointment {}", cs.getGoogleEventId(), appt.getId());
            }

        } catch (CalendarException e) {
            fail(cs, e.getMessage());
        } catch (Exception e) {
            fail(cs, e.getClass().getSimpleName());
        }
    }

    /** Event content: scheduling facts only. Never symptoms / AI summaries / prescriptions. */
    private GoogleCalendarClient.CalendarEventDetails buildDetails(Appointment appt, User owner) {
        String title = "Appointment – Dr. "
                + appt.getDoctor().getUser().getLastName()
                + " (" + appt.getDoctor().getSpecialisation() + ")";
        String description = "Healthcare appointment"
                + (owner.equals(appt.getPatient().getUser())
                   ? " with Dr. " + appt.getDoctor().getUser().getLastName()
                   : " with patient " + appt.getPatient().getUser().getFirstName())
                + ". Duration: "
                + java.time.Duration.between(appt.getAppointmentStart(), appt.getAppointmentEnd()).toMinutes()
                + " minutes.";
        return new GoogleCalendarClient.CalendarEventDetails(
                title, description, appt.getAppointmentStart(), appt.getAppointmentEnd());
    }

    private void finish(CalendarSync cs, CalendarSyncStatus status, String message) {
        cs.setStatus(status);
        cs.setErrorMessage(message);
        calendarSyncRepository.save(cs);
    }

    private void fail(CalendarSync cs, String rawMessage) {
        String msg = rawMessage == null ? "Unknown calendar error" : rawMessage;
        if (msg.length() > 500) msg = msg.substring(0, 500);
        cs.setStatus(CalendarSyncStatus.FAILED);
        cs.setErrorMessage(msg);
        calendarSyncRepository.save(cs);
        log.warn("Calendar sync failed id={} type={} attempt={} reason={}",
                cs.getId(), cs.getAppointment().getId(), cs.getAttemptCount(), msg);
    }
}
