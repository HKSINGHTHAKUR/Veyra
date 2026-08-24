package com.healthcare.appointment.integration.calendar;

public class CalendarException extends Exception {
    public CalendarException(String message) { super(message); }
    public CalendarException(String message, Throwable cause) { super(message, cause); }
}
