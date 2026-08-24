package com.healthcare.appointment.integration.email;

import com.healthcare.appointment.entity.Notification;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake EmailService for tests (profile test). No SMTP, deterministic.
 * Can be configured to fail for failure-isolation tests.
 */
@Slf4j
@Service
@Profile("test")
@Primary
public class FakeEmailService implements EmailService {

    private final AtomicBoolean shouldFail = new AtomicBoolean(false);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicInteger maxFailsBeforeSuccess = new AtomicInteger(0);

    @Getter
    private final List<Notification> sent = new ArrayList<>();

    public void setShouldFail(boolean fail) { shouldFail.set(fail); }

    /**
     * Fail first N calls, then succeed – for retry tests.
     */
    public void failNext(int n) {
        shouldFail.set(true);
        failCount.set(0);
        maxFailsBeforeSuccess.set(n);
    }

    public void reset() {
        shouldFail.set(false);
        failCount.set(0);
        maxFailsBeforeSuccess.set(0);
        sent.clear();
    }

    @Override
    public void send(Notification notification) throws EmailException {
        if (shouldFail.get()) {
            int current = failCount.incrementAndGet();
            if (current <= maxFailsBeforeSuccess.get() || maxFailsBeforeSuccess.get() == 0) {
                log.info("FakeEmailService failing for notification {} attempt {}", notification.getId(), current);
                throw new EmailException("Fake failure for testing");
            } else {
                shouldFail.set(false);
            }
        }
        sent.add(notification);
        log.info("FakeEmailService sent notification {} to {}", notification.getId(), notification.getUser().getEmail());
    }
}
