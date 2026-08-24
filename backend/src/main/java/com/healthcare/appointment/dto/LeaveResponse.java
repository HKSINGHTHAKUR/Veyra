package com.healthcare.appointment.dto;

import com.healthcare.appointment.entity.DoctorLeave;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public class LeaveResponse {

    private final UUID id;
    private final UUID doctorId;
    private final LocalDate leaveDate;
    private final String reason;
    private final Instant createdAt;

    public LeaveResponse(DoctorLeave leave) {
        this.id        = leave.getId();
        this.doctorId  = leave.getDoctor().getId();
        this.leaveDate = leave.getLeaveDate();
        this.reason    = leave.getReason();
        this.createdAt = leave.getCreatedAt();
    }
}
