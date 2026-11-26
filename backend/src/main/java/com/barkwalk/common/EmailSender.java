package com.barkwalk.common;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Envoi email via Brevo API (prioritaire) ou SMTP Spring Mail (Mailhog / Brevo SMTP).
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private final MailProperties mailProperties;
    private final JavaMailSender javaMailSender;

    public EmailSender(MailProperties mailProperties, JavaMailSender javaMailSender) {
        this.mailProperties = mailProperties;
        this.javaMailSender = javaMailSender;
    }

    public void send(String to, String subject, String textBody) {
        if (to == null || to.isBlank()) {
            log.warn("Skip email: empty recipient subject={}", subject);
            return;
        }
        if (!mailProperties.isEnabled()) {
            log.info("EMAIL disabled: to={} subject={}", to, subject);
            return;
        }
        if (mailProperties.getBrevoApiKey() != null && !mailProperties.getBrevoApiKey().isBlank()) {
            sendViaBrevo(to, subject, textBody);
            return;
        }
        sendViaSmtp(to, subject, textBody);
    }

    private void sendViaBrevo(String to, String subject, String textBody) {
        try {
            RestClient.create()
                    .post()
                    .uri(BREVO_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", mailProperties.getBrevoApiKey())
                    .body(Map.of(
                            "sender", Map.of("email", mailProperties.getFrom(), "name", "Bark & Walk"),
                            "to", List.of(Map.of("email", to)),
                            "subject", subject,
                            "textContent", textBody))
                    .retrieve()
                    .toBodilessEntity();
            log.info("EMAIL Brevo sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("EMAIL Brevo failed to={} subject={}: {}", to, subject, e.getMessage());
        }
    }

    private void sendViaSmtp(String to, String subject, String textBody) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(textBody);
            javaMailSender.send(message);
            log.info("EMAIL SMTP sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("EMAIL SMTP failed to={} subject={}: {}", to, subject, e.getMessage());
        }
    }
}
