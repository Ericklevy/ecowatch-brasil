package br.com.ecowatch.notification_service.infrastructure.messaging;

import br.com.ecowatch.notification_service.domain.model.AlertResult;
import br.com.ecowatch.notification_service.infrastructure.email.EmailService;
import br.com.ecowatch.notification_service.infrastructure.firebase.FirebaseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Lazy(false)
@RequiredArgsConstructor
public class AlertNotificationConsumer {

    private final EmailService emailService;
    private final FirebaseNotificationService firebaseService;

    // Token de exemplo para testes (você vai obter do frontend depois)
    private static final String TEST_FCM_TOKEN = "TOKEN_DO_DISPOSITIVO_AQUI";

    @RabbitListener(queues = RabbitMQConfig.ALERT_QUEUE)
    public void receiveAlert(AlertResult alert) {
        log.info("📨 NOTIFICAÇÃO RECEBIDA: {}", alert.message());
        log.info("   Município: {} | Tipo: {}", alert.municipio(), alert.type().getLabel());

        // 1. E-mail com assunto em PT-BR e corpo rico
        String subject = "🚨 ALERTA: " + alert.type().getLabel() + " em " + alert.municipio();
        String emailBody = buildEmailBody(alert);
        emailService.sendAlertEmail(subject, emailBody);

        // 2. Push (se tiver token)
        String pushTitle = "🚨 " + alert.type().getLabel();
        String pushBody = alert.message();
        firebaseService.sendPushNotification(TEST_FCM_TOKEN, pushTitle, pushBody);
    }

    private String buildEmailBody(AlertResult alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🚨 ").append(alert.type().getLabel().toUpperCase()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        sb.append("📍 Local: ").append(alert.municipio());
        if (alert.estado() != null) {
            sb.append("/").append(alert.estado());
        }
        sb.append("\n\n");

        sb.append("📊 Dados Climáticos:\n");
        sb.append("   🌡️ Temperatura: ").append(String.format("%.1f°C", alert.temperatura())).append("\n");
        sb.append("   💧 Umidade: ").append(String.format("%.1f%%", alert.umidade())).append("\n");
        sb.append("   🌧️ Precipitação: ").append(String.format("%.1fmm", alert.precipitacao())).append("\n\n");

        sb.append("📋 Detalhes:\n");
        sb.append(alert.message()).append("\n\n");

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Monitorado pelo EcoWatch Brasil 🌿\n");
        sb.append("Sistema de Monitoramento Ambiental\n");

        return sb.toString();
    }
}