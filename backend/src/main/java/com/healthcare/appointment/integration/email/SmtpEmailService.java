package com.healthcare.appointment.integration.email;

import com.healthcare.appointment.entity.Notification;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!test")
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void send(Notification notification) throws EmailException {
        try {
            String to = Recipients.resolve(notification.getUser().getEmail());
            if (to == null || to.isBlank()) throw new EmailException("Recipient email missing");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(notification.getSubject() != null ? notification.getSubject() : "Healthcare Notification");
            helper.setText(notification.getBody() != null ? notification.getBody() : "", false);
            // From could be configured via spring.mail.properties; default is mailSender's username

            mailSender.send(message);
            log.info("Email sent for notification {} to {} type {}", notification.getId(), to, notification.getType());

        } catch (EmailException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailException("SMTP send failed: " + e.getMessage(), e);
        }
    }
}
