package com.healthcare.appointment.integration.email;

import com.healthcare.appointment.entity.Notification;

/**
 * Abstraction for sending emails. Business services depend on this,
 * not on JavaMail implementation.
 */
public interface EmailService {
    void send(Notification notification) throws EmailException;
}
