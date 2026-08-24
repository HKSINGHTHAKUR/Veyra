package com.healthcare.appointment.scheduler;

import com.healthcare.appointment.service.CalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background worker: processes PENDING (and retryable FAILED) CalendarSync rows.
 * Runs on the same conventions as NotificationProcessor. Google API failures are
 * caught inside CalendarSyncService and never propagate to booking flows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarSyncWorker {

    private final CalendarSyncService calendarSyncService;

    @Scheduled(fixedDelayString = "${app.google.calendar.worker-interval-ms:60000}")
    public void processPending() {
        calendarSyncService.processPending();
    }

    /** Deterministic entry point for tests. */
    public void processForTest() {
        calendarSyncService.processForTest();
    }
}
