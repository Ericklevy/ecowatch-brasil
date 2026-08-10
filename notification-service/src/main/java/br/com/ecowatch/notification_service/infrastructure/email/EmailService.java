package br.com.ecowatch.notification_service.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${notification.email.default.to}")
    private String defaultTo;

    public void sendAlertEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            to = defaultTo;
            log.info("📧 Destinatário não informado, usando padrão: {}", to);
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("✅ E-mail enviado para {}", to);
        } catch (Exception e) {
            log.error("❌ Erro ao enviar e-mail: {}", e.getMessage());
        }
    }

    public void sendAlertEmail(String subject, String body) {
        sendAlertEmail(null, subject, body);
    }
}