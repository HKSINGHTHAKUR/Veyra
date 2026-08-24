package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.NotificationResponse;
import com.healthcare.appointment.entity.Notification;
import com.healthcare.appointment.entity.NotificationStatus;
import com.healthcare.appointment.entity.NotificationType;
import com.healthcare.appointment.repository.NotificationRepository;
import com.healthcare.appointment.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        UUID userId = currentUserService.getCurrentUserId();
        List<Notification> list = notificationRepository.findByUserId(userId);

        // Filtering is done in-memory for simplicity; for large scale, add repository query
        if (status != null && !status.isBlank()) {
            try {
                NotificationStatus s = NotificationStatus.valueOf(status.toUpperCase());
                list = list.stream().filter(n -> n.getStatus() == s).collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }
        if (type != null && !type.isBlank()) {
            try {
                NotificationType t = NotificationType.valueOf(type.toUpperCase());
                list = list.stream().filter(n -> n.getType() == t).collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }

        // Ownership already enforced via userId filter; never trust frontend userId
        return ResponseEntity.ok(list.stream().map(NotificationResponse::new).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> getOne(@PathVariable UUID id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new com.healthcare.appointment.exception.ResourceNotFoundException("Notification", id));
        // Ownership check
        UUID currentUserId = currentUserService.getCurrentUserId();
        var currentUser = currentUserService.getCurrentUser();
        boolean isAdmin = "ADMIN".equals(currentUser.getRole().name());
        if (!n.getUser().getId().equals(currentUserId) && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return ResponseEntity.ok(new NotificationResponse(n));
    }
}
