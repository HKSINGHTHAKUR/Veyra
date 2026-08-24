package com.healthcare.appointment.exception;

/** Thrown for invalid business logic requests that don't fit validation errors. Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
