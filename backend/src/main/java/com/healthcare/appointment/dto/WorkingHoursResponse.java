package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.DoctorWorkingHours;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Getter
public class WorkingHoursResponse {

    private final UUID id;
    private final UUID doctorId;
    private final DayOfWeek dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final int slotDurationMinutes;
    private final boolean active;

    public WorkingHoursResponse(DoctorWorkingHours wh) {
        this.id                  = wh.getId();
        this.doctorId            = wh.getDoctor().getId();
        this.dayOfWeek           = wh.getDayOfWeek();
        this.startTime           = wh.getStartTime();
        this.endTime             = wh.getEndTime();
        this.slotDurationMinutes = wh.getSlotDurationMinutes();
        this.active              = wh.isActive();
    }
}
