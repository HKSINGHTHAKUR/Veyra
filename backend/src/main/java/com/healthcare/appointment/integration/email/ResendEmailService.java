package com.healthcare.appointment.integration.email;

import com.healthcare.appointment.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production email delivery via the Resend HTTPS Email API.
 *
 * Transport only: the notification layer keeps calling
 * {@link EmailService#send(Notification)} and never sees that SMTP was replaced.
 * Selected when {@code MAIL_PROVIDER=resend} (the default). No SMTP ports are
 * used, so it works from environments that block outbound SMTP (e.g. Render).
 *
 * SECURITY: the API key is never logged or exposed; the Authorization header is
 * never logged; recipients are resolved server-side (demo accounts -> demo
 * destination, real users -> their own email) and never from the frontend.
 */
@Slf4j
@Service
@Profile("!test")
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "resend", matchIfMissing = true)
public class ResendEmailService implements EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final RestTemplate rest = new RestTemplate();

    @Value("${app.resend.api-key}")
    private String apiKey;

    @Value("${app.resend.from-email}")
    private String fromEmail;

    @Value("${app.resend.from-name:Veyra}")
    private String fromName;

    @Override
    public void send(Notification notification) throws EmailException {
        try {
            String to = Recipients.resolve(notification.getUser().getEmail());
            if (to == null || to.isBlank()) throw new EmailException("Recipient email missing");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", fromName + " <" + fromEmail + ">");
            body.put("to", List.of(to));
            body.put("subject", notification.getSubject() != null ? notification.getSubject() : "Healthcare Notification");
            body.put("text", notification.getBody() != null ? notification.getBody() : "");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rest.postForEntity(RESEND_URL, entity, String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new EmailException("Resend API returned " + resp.getStatusCode());
            }

            log.info("Email sent for notification {} to {} type {}", notification.getId(), to, notification.getType());

        } catch (EmailException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailException("Resend send failed: " + e.getMessage(), e);
        }
    }
}
