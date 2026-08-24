package com.healthcare.appointment.event;

import com.healthcare.appointment.service.AiSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiSummaryEventListener {

    private final AiSummaryService aiSummaryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentConfirmed(AppointmentConfirmedEvent event) {
        try {
            log.info("Handling pre-visit AI for appointment {}", event.getAppointmentId());
            aiSummaryService.generatePreVisit(event.getAppointmentId());
        } catch (Exception e) {
            log.warn("Pre-visit handling failed for {}: {}", event.getAppointmentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConsultationCompleted(ConsultationCompletedEvent event) {
        try {
            log.info("Handling post-visit AI for appointment {}", event.getAppointmentId());
            aiSummaryService.generatePostVisit(event.getAppointmentId());
        } catch (Exception e) {
            log.warn("Post-visit handling failed for {}: {}", event.getAppointmentId(), e.getMessage());
        }
    }
}
