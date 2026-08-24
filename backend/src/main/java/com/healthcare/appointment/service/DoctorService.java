package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.*;
import com.healthcare.appointment.entity.Doctor;
import com.healthcare.appointment.entity.Role;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.exception.EmailAlreadyExistsException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.repository.DoctorRepository;
import com.healthcare.appointment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DoctorResponse createDoctor(CreateDoctorRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getTemporaryPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setRole(Role.DOCTOR);
        user = userRepository.save(user);

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialisation(request.getSpecialisation());
        doctor.setBio(request.getBio());
        doctor.setConsultationFee(request.getConsultationFee());
        doctor = doctorRepository.save(doctor);

        return new DoctorResponse(doctor);
    }

    @Transactional
    public DoctorResponse updateDoctor(UUID doctorId, UpdateDoctorRequest request) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        User user = doctor.getUser();

        if (request.getFirstName() != null)    user.setFirstName(request.getFirstName());
        if (request.getLastName() != null)     user.setLastName(request.getLastName());
        if (request.getPhone() != null)        user.setPhone(request.getPhone());
        if (request.getSpecialisation() != null) doctor.setSpecialisation(request.getSpecialisation());
        if (request.getBio() != null)          doctor.setBio(request.getBio());
        if (request.getConsultationFee() != null) doctor.setConsultationFee(request.getConsultationFee());

        userRepository.save(user);
        doctor = doctorRepository.save(doctor);
        return new DoctorResponse(doctor);
    }

    @Transactional
    public DoctorResponse deactivateDoctor(UUID doctorId) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        doctor.setActive(false);
        doctor.getUser().setActive(false);
        userRepository.save(doctor.getUser());
        return new DoctorResponse(doctorRepository.save(doctor));
    }

    @Transactional
    public DoctorResponse activateDoctor(UUID doctorId) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        doctor.setActive(true);
        doctor.getUser().setActive(true);
        userRepository.save(doctor.getUser());
        return new DoctorResponse(doctorRepository.save(doctor));
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctorById(UUID doctorId) {
        return new DoctorResponse(findDoctorOrThrow(doctorId));
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> getAllDoctors() {
        return doctorRepository.findAll()
                .stream()
                .map(DoctorResponse::new)
                .toList();
    }

    /**
     * Patient-facing search. Only active doctors are returned.
     * specialisation filter is optional and case-insensitive.
     */
    @Transactional(readOnly = true)
    public List<DoctorSummaryResponse> searchActiveDoctors(String specialisation) {
        List<Doctor> doctors = (specialisation == null || specialisation.isBlank())
                ? doctorRepository.findByActive(true)
                : doctorRepository.findBySpecialisationContainingIgnoreCaseAndActive(specialisation, true);

        return doctors.stream()
                .map(DoctorSummaryResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorSummaryResponse getDoctorSummary(UUID doctorId) {
        Doctor doctor = findDoctorOrThrow(doctorId);
        if (!doctor.isActive()) {
            throw new ResourceNotFoundException("Doctor", doctorId);
        }
        return new DoctorSummaryResponse(doctor);
    }

    private Doctor findDoctorOrThrow(UUID doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
    }
}
