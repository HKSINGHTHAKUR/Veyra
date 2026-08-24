package com.healthcare.appointment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ConsultationCompletedEvent extends ApplicationEvent {
    private final UUID appointmentId;
    public ConsultationCompletedEvent(Object source, UUID appointmentId) {
        super(source);
        this.appointmentId = appointmentId;
    }
}
