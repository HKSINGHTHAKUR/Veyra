package com.healthcare.appointment.scheduler;

import com.healthcare.appointment.entity.Notification;
import com.healthcare.appointment.entity.NotificationStatus;
import com.healthcare.appointment.integration.email.EmailException;
import com.healthcare.appointment.integration.email.EmailService;
import com.healthcare.appointment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background worker: claims PENDING (and retryable FAILED) notifications
 * and sends emails via EmailService. Uses atomic status transition to
 * prevent duplicate sends with concurrent workers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Value("${app.notifications.max-retries:3}")
    private int maxRetries;

    @Value("${app.notifications.worker-batch-size:20}")
    private int batchSize;

    // Simple backoff in minutes: 1,5,15 for attempts 1,2,3
    private long backoffMinutes(int attemptCount) {
        if (attemptCount == 1) return 1;
        if (attemptCount == 2) return 5;
        return 15;
    }

    @Scheduled(fixedDelayString = "${app.notifications.worker-interval-ms:30000}")
    @Transactional
    public void processPending() {
        List<Notification> pending = notificationRepository.findByStatus(NotificationStatus.PENDING);
        // limit batch to avoid long transactions
        pending.stream().limit(batchSize).forEach(this::tryProcess);
        // Also retry FAILED that are eligible
        List<Notification> retryable = notificationRepository.findRetryable(List.of(NotificationStatus.FAILED), maxRetries);
        for (Notification n : retryable) {
            if (isEligibleForRetry(n)) {
                tryProcess(n);
            }
        }
    }

    private boolean isEligibleForRetry(Notification n) {
        if (n.getLastAttemptAt() == null) return true;
        long backoff = backoffMinutes(n.getAttemptCount());
        return Instant.now().isAfter(n.getLastAttemptAt().plusSeconds(backoff * 60));
    }

    private void tryProcess(Notification notification) {
        NotificationStatus expected = notification.getStatus();
        if (expected != NotificationStatus.PENDING && expected != NotificationStatus.FAILED) return;

        // Atomic claim: single conditional UPDATE flips PENDING/FAILED -> PROCESSING and
        // increments attemptCount. A competing worker's UPDATE matches 0 rows, preventing
        // duplicate sends even if two schedulers run concurrently.
        int claimed = notificationRepository.claim(notification.getId(), expected,
                NotificationStatus.PROCESSING, Instant.now());
        if (claimed == 0) {
            log.debug("Notification {} already claimed by another worker", notification.getId());
            return;
        }
        Notification n = notificationRepository.findById(notification.getId()).orElse(null);
        if (n == null) return;

        try {
            emailService.send(n);
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(Instant.now());
            n.setFailureReason(null);
            notificationRepository.save(n);
            log.info("Notification sent id={} type={} to user={} attempt={}", n.getId(), n.getType(), n.getUser().getId(), n.getAttemptCount());
        } catch (EmailException e) {
            n.setStatus(NotificationStatus.FAILED);
            n.setFailureReason(truncate(e.getMessage()));
            n.setLastAttemptAt(Instant.now());
            notificationRepository.save(n);
            log.warn("Notification failed id={} type={} attempt={} reason={}", n.getId(), n.getType(), n.getAttemptCount(), e.getMessage());
        } catch (Exception e) {
            n.setStatus(NotificationStatus.FAILED);
            n.setFailureReason(truncate(e.getMessage()));
            n.setLastAttemptAt(Instant.now());
            notificationRepository.save(n);
            log.warn("Notification failed unexpected id={} type={} reason={}", n.getId(), n.getType(), e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    // For deterministic testing – call directly (must be transactional for claim)
    @Transactional
    public void processForTest() {
        processPending();
    }
}
