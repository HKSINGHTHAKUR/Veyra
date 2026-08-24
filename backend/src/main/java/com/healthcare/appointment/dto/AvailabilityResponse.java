package com.healthcare.appointment.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Availability for a given doctor on a given date.
 * Slots list contains only future, non-past slots.
 * A slot with available=false is occupied (booked or held).
 */
@Getter
public class AvailabilityResponse {

    private final UUID doctorId;
    private final LocalDate date;
    private final List<SlotDto> slots;

    public AvailabilityResponse(UUID doctorId, LocalDate date, List<SlotDto> slots) {
        this.doctorId = doctorId;
        this.date     = date;
        this.slots    = slots;
    }

    public static AvailabilityResponse empty(UUID doctorId, LocalDate date) {
        return new AvailabilityResponse(doctorId, date, List.of());
    }

    /** True if at least one slot is available. */
    public boolean isAvailable() {
        return slots.stream().anyMatch(SlotDto::isAvailable);
    }
}
