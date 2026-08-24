package com.healthcare.appointment.dto;

import lombok.Getter;

import java.util.UUID;

/** Safe connection status – never exposes tokens or event IDs. */
@Getter
public class CalendarStatusResponse {
    private final boolean connected;
    private final UUID userId;

    public CalendarStatusResponse(boolean connected, UUID userId) {
        this.connected = connected;
        this.userId = userId;
    }
}
