package com.healthcare.appointment.dto;

import lombok.Getter;

import java.time.Instant;

/**
 * A single appointment slot in an availability response.
 * slotStart and slotEnd are UTC Instants.
 */
@Getter
public class SlotDto {

    private final Instant slotStart;
    private final Instant slotEnd;
    private final boolean available;

    public SlotDto(Instant slotStart, Instant slotEnd, boolean available) {
        this.slotStart = slotStart;
        this.slotEnd   = slotEnd;
        this.available = available;
    }
}
