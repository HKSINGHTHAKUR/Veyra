package com.healthcare.appointment.exception;

/** Thrown when a create request conflicts with existing data. Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
