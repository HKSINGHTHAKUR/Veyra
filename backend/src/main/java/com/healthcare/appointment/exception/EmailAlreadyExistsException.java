package com.healthcare.appointment.exception;

/** Thrown when attempting to register with an email that is already in use. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email);
    }
}
