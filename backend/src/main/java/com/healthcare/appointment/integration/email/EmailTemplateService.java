package com.healthcare.appointment.integration.email;

import com.healthcare.appointment.entity.Appointment;
import com.healthcare.appointment.entity.Doctor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EmailTemplateService {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    // User-facing appointment times are shown in the user's zone (India).
    // The underlying Instant is never modified — only formatted for display.
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    /** Human-readable appointment time in the user's zone (e.g. "August 27, 2026 at 9:00 PM"). */
    public static String formatAppointmentTime(Instant instant) {
        return instant == null ? "" : TIME_FMT.withZone(USER_ZONE).format(instant);
    }

    /** Human-readable calendar date (e.g. "August 27, 2026"). */
    public static String formatDate(LocalDate date) {
        return date == null ? "" : DATE_FMT.format(date);
    }

    // Templates kept as simple Java strings (maintainable, no HTML builder in service)

    public String confirmationSubject(Appointment appt) {
        return "Appointment Confirmed – Dr. " + appt.getDoctor().getUser().getLastName();
    }

    public String confirmationBody(Appointment appt) {
        return """
                Hello %s,

                Your appointment with Dr. %s %s (%s) has been confirmed.

                Date & Time: %s
                Please arrive 10 minutes early.

                View details: %s/appointments/%s

                Thank you for choosing our healthcare service.
                """.formatted(
                appt.getPatient().getUser().getFirstName(),
                appt.getDoctor().getUser().getFirstName(), appt.getDoctor().getUser().getLastName(),
                appt.getDoctor().getSpecialisation(),
                formatAppointmentTime(appt.getAppointmentStart()),
                frontendUrl, appt.getId()
        );
    }

    public String cancellationSubject(Appointment appt) {
        return "Appointment Cancelled";
    }

    public String cancellationBody(Appointment appt) {
        return """
                Hello %s,

                Your appointment with Dr. %s %s on %s has been cancelled.

                If you need to reschedule, please visit: %s

                Regards,
                Healthcare Team
                """.formatted(
                appt.getPatient().getUser().getFirstName(),
                appt.getDoctor().getUser().getFirstName(), appt.getDoctor().getUser().getLastName(),
                formatAppointmentTime(appt.getAppointmentStart()),
                frontendUrl
        );
    }

    public String rescheduleSubject(Appointment appt, String oldTime) {
        return "Appointment Rescheduled";
    }

    public String rescheduleBody(Appointment appt, String oldTime) {
        return """
                Hello %s,

                Your appointment has been rescheduled.

                Previous: %s
                New: %s
                Doctor: Dr. %s %s (%s)

                Details: %s/appointments/%s
                """.formatted(
                appt.getPatient().getUser().getFirstName(),
                oldTime,
                formatAppointmentTime(appt.getAppointmentStart()),
                appt.getDoctor().getUser().getFirstName(), appt.getDoctor().getUser().getLastName(),
                appt.getDoctor().getSpecialisation(),
                frontendUrl, appt.getId()
        );
    }

    public String reminderSubject(Appointment appt) {
        return "Reminder: Appointment tomorrow with Dr. " + appt.getDoctor().getUser().getLastName();
    }

    public String reminderBody(Appointment appt) {
        return """
                Hello %s,

                This is a friendly reminder of your appointment tomorrow.

                Doctor: Dr. %s %s (%s)
                Time: %s

                Please confirm or reschedule if needed: %s

                See you soon!
                """.formatted(
                appt.getPatient().getUser().getFirstName(),
                appt.getDoctor().getUser().getFirstName(), appt.getDoctor().getUser().getLastName(),
                appt.getDoctor().getSpecialisation(),
                formatAppointmentTime(appt.getAppointmentStart()),
                frontendUrl
        );
    }

    public String doctorLeaveSubject(Doctor doctor, java.time.LocalDate leaveDate) {
        return "Doctor Unavailable – Action Required";
    }

    public String doctorLeaveBody(Appointment appt, java.time.LocalDate leaveDate) {
        return """
                Hello %s,

                We regret to inform you that Dr. %s %s (%s) is unavailable on %s.

                Your appointment on %s is affected.

                Please contact us or reschedule via: %s
                We apologize for the inconvenience and will help you find an alternative slot.

                Regards,
                Healthcare Team
                """.formatted(
                appt.getPatient().getUser().getFirstName(),
                appt.getDoctor().getUser().getFirstName(), appt.getDoctor().getUser().getLastName(),
                appt.getDoctor().getSpecialisation(),
                formatDate(leaveDate),
                formatAppointmentTime(appt.getAppointmentStart()),
                frontendUrl
        );
    }
}
