package com.healthcare.appointment.scheduler;

import com.healthcare.appointment.entity.SlotHoldStatus;
import com.healthcare.appointment.repository.SlotHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final SlotHoldRepository slotHoldRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireStaleHolds() {
        int count = slotHoldRepository.expireStaleHolds(Instant.now(), SlotHoldStatus.ACTIVE, SlotHoldStatus.EXPIRED);
        if (count > 0) {
            log.info("Expired {} stale holds", count);
        }
    }
}
