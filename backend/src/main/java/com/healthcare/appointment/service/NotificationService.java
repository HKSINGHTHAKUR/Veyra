package com.healthcare.appointment.service;

import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification createNotification(User user, UUID appointmentId, NotificationType type, String subject, String body) {
        Notification n = new Notification();
        n.setUser(user);
        n.setAppointmentId(appointmentId);
        n.setType(type);
        n.setStatus(NotificationStatus.PENDING);
        n.setSubject(subject);
        n.setBody(body);
        n.setAttemptCount(0);
        return notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> findByUserId(UUID userId) {
        return notificationRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> findByUserIdAndStatus(UUID userId, NotificationStatus status) {
        // simple filter; could add query
        return notificationRepository.findByUserId(userId).stream().filter(n -> n.getStatus() == status).toList();
    }

    /**
     * For reminder duplicate prevention: check if reminder already exists for appointment.
     */
    public boolean existsReminderForAppointment(UUID appointmentId) {
        return notificationRepository.findByAppointmentId(appointmentId).stream()
                .anyMatch(n -> n.getType() == NotificationType.APPOINTMENT_REMINDER);
    }

    /**
     * For doctor leave duplicate prevention
     */
    public boolean existsDoctorLeaveForAppointment(UUID appointmentId) {
        return notificationRepository.findByAppointmentId(appointmentId).stream()
                .anyMatch(n -> n.getType() == NotificationType.DOCTOR_LEAVE);
    }
}
