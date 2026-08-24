package com.healthcare.appointment.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error body returned by the API for all error responses.
 * fieldErrors is omitted from the JSON when null (validation errors only).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String message;
    private final Instant timestamp;
    private final Map<String, String> fieldErrors;

    private ErrorResponse(int status, String message, Map<String, String> fieldErrors) {
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now();
        this.fieldErrors = fieldErrors;
    }

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, null);
    }

    public static ErrorResponse withFieldErrors(int status, String message,
                                                Map<String, String> fieldErrors) {
        return new ErrorResponse(status, message, fieldErrors);
    }

    public int getStatus()                    { return status; }
    public String getMessage()                { return message; }
    public Instant getTimestamp()             { return timestamp; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
