package com.healthcare.appointment.scheduler;

import com.healthcare.appointment.entity.Appointment;
import com.healthcare.appointment.entity.AppointmentStatus;
import com.healthcare.appointment.entity.NotificationType;
import com.healthcare.appointment.integration.email.EmailTemplateService;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final EmailTemplateService templateService;

    @Value("${app.notifications.reminder-window-minutes:60}")
    private int reminderWindowMinutes;

    /**
     * Every minute, find appointments ~24h from now and create reminder if not already exists.
     * Uses window: 24h ± reminderWindow/2, efficient indexed query.
     */
    @Scheduled(fixedDelayString = "${app.notifications.reminder-interval-ms:60000}")
    @Transactional
    public void createReminders() {
        createRemindersForTest(Instant.now());
    }

    // For deterministic testing
    @Transactional
    public void createRemindersForTest(Instant now) {
        Instant windowStart = now.plus(24, ChronoUnit.HOURS).minusSeconds(reminderWindowMinutes * 30L);
        Instant windowEnd = now.plus(24, ChronoUnit.HOURS).plusSeconds(reminderWindowMinutes * 30L);
        List<Appointment> inWindow = appointmentRepository
                .findByAppointmentStartBetweenAndStatusIn(windowStart, windowEnd, List.of(AppointmentStatus.BOOKED, AppointmentStatus.RESCHEDULED));
        for (Appointment appt : inWindow) {
            if (notificationService.existsReminderForAppointment(appt.getId())) continue; // idempotent
            notificationService.createNotification(appt.getPatient().getUser(), appt.getId(),
                    NotificationType.APPOINTMENT_REMINDER,
                    templateService.reminderSubject(appt), templateService.reminderBody(appt));
            log.info("Created 24h reminder for appointment {}", appt.getId());
        }
    }
}
