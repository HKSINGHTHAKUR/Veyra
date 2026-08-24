package com.healthcare.appointment.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class AppointmentConfirmedEvent extends ApplicationEvent {
    private final UUID appointmentId;
    public AppointmentConfirmedEvent(Object source, UUID appointmentId) {
        super(source);
        this.appointmentId = appointmentId;
    }
}
