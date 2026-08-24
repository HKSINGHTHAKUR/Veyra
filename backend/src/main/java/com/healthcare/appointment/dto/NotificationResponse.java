package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.Notification;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class NotificationResponse {
    private final UUID id;
    private final UUID userId;
    private final UUID appointmentId;
    private final String type;
    private final String status;
    private final String subject;
    private final String body;
    private final int attemptCount;
    private final Instant lastAttemptAt;
    private final Instant sentAt;
    private final String failureReason;
    private final Instant createdAt;

    public NotificationResponse(Notification n) {
        this.id = n.getId();
        this.userId = n.getUser().getId();
        this.appointmentId = n.getAppointmentId();
        this.type = n.getType().name();
        this.status = n.getStatus().name();
        this.subject = n.getSubject();
        this.body = n.getBody();
        this.attemptCount = n.getAttemptCount();
        this.lastAttemptAt = n.getLastAttemptAt();
        this.sentAt = n.getSentAt();
        this.failureReason = n.getFailureReason();
        this.createdAt = n.getCreatedAt();
    }
}
