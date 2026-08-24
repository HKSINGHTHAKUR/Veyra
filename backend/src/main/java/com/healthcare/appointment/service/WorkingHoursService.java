package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.WorkingHoursRequest;
import com.healthcare.appointment.dto.WorkingHoursResponse;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.DoctorWorkingHours;
import com.healthcare.appointment.exception.BadRequestException;
import com.healthcare.appointment.exception.ConflictException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.repository.DoctorRepository;
import com.healthcare.appointment.repository.DoctorWorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkingHoursService {

    private final DoctorRepository doctorRepository;
    private final DoctorWorkingHoursRepository workingHoursRepository;

    /**
     * Creates working hours for a specific day.
     * Fails with 409 if the doctor already has a schedule for that day.
     * Use updateWorkingHours to modify an existing schedule.
     */
    @Transactional
    public WorkingHoursResponse createWorkingHours(UUID doctorId, WorkingHoursRequest request) {
        Doctor doctor = findActiveDoctor(doctorId);
        validateTimes(request);

        if (workingHoursRepository.findByDoctorIdAndDayOfWeek(doctorId, request.getDayOfWeek()).isPresent()) {
            throw new ConflictException(
                    "Working hours already exist for " + request.getDayOfWeek()
                    + ". Use PUT to update the existing schedule.");
        }

        DoctorWorkingHours wh = new DoctorWorkingHours();
        wh.setDoctor(doctor);
        applyRequest(wh, request);
        return new WorkingHoursResponse(workingHoursRepository.save(wh));
    }

    /**
     * Updates an existing working hours record.
     * Validates ownership (the record must belong to the specified doctor).
     */
    @Transactional
    public WorkingHoursResponse updateWorkingHours(UUID doctorId, UUID workingHoursId,
                                                   WorkingHoursRequest request) {
        findActiveDoctor(doctorId);
        validateTimes(request);

        DoctorWorkingHours wh = workingHoursRepository.findById(workingHoursId)
                .orElseThrow(() -> new ResourceNotFoundException("Working hours", workingHoursId));

        if (!wh.getDoctor().getId().equals(doctorId)) {
            throw new ResourceNotFoundException("Working hours", workingHoursId);
        }

        applyRequest(wh, request);
        return new WorkingHoursResponse(workingHoursRepository.save(wh));
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getWorkingHours(UUID doctorId) {
        findDoctorOrThrow(doctorId);
        return workingHoursRepository.findByDoctorId(doctorId)
                .stream()
                .map(WorkingHoursResponse::new)
                .toList();
    }

    @Transactional
    public void deleteWorkingHours(UUID doctorId, UUID workingHoursId) {
        findDoctorOrThrow(doctorId);
        DoctorWorkingHours wh = workingHoursRepository.findById(workingHoursId)
                .orElseThrow(() -> new ResourceNotFoundException("Working hours", workingHoursId));

        if (!wh.getDoctor().getId().equals(doctorId)) {
            throw new ResourceNotFoundException("Working hours", workingHoursId);
        }

        workingHoursRepository.delete(wh);
    }

    private void validateTimes(WorkingHoursRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }
        long range = java.time.Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
        if (request.getSlotDurationMinutes() > range) {
            throw new BadRequestException(
                    "Slot duration (" + request.getSlotDurationMinutes()
                    + " min) exceeds the working window (" + range + " min)");
        }
    }

    private void applyRequest(DoctorWorkingHours wh, WorkingHoursRequest request) {
        wh.setDayOfWeek(request.getDayOfWeek());
        wh.setStartTime(request.getStartTime());
        wh.setEndTime(request.getEndTime());
        wh.setSlotDurationMinutes(request.getSlotDurationMinutes());
        wh.setActive(true);
    }

    private Doctor findActiveDoctor(UUID doctorId) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        if (!doctor.isActive()) {
            throw new BadRequestException("Doctor is not active");
        }
        return doctor;
    }

    private Doctor findDoctorOrThrow(UUID doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
    }
}
