package br.com.ecowatch.notification_service.infrastructure.messaging;

import br.com.ecowatch.notification_service.domain.model.AlertResult;
import br.com.ecowatch.notification_service.domain.model.AlertType;
import br.com.ecowatch.notification_service.infrastructure.email.EmailService;
import br.com.ecowatch.notification_service.infrastructure.firebase.FirebaseNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertNotificationConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private FirebaseNotificationService firebaseService;

    @InjectMocks
    private AlertNotificationConsumer consumer;

    @Test
    void shouldProcessAlertAndDispatchEmailAndPush() {
        AlertResult alert = new AlertResult(
                true,
                AlertType.EXTREME_RAIN,
                "Porto Alegre",
                "RS",
                "Chuva torrencial de 115mm detectada em 1 hora.",
                21.5,
                98.0,
                115.0
        );

        consumer.receiveAlert(alert);

        // Verifica envio de e-mail
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailService, times(1)).sendAlertEmail(subjectCaptor.capture(), bodyCaptor.capture());

        String subject = subjectCaptor.getValue();
        String body = bodyCaptor.getValue();

        assertTrue(subject.contains("Chuva Extrema"), "O assunto deve conter o tipo do alerta");
        assertTrue(subject.contains("Porto Alegre"), "O assunto deve conter o município");

        assertTrue(body.contains("Porto Alegre/RS"), "O corpo deve conter cidade e UF");
        assertTrue(body.contains("21.5°C") || body.contains("21,5°C"), "O corpo deve conter a temperatura");
        assertTrue(body.contains("98.0%") || body.contains("98,0%"), "O corpo deve conter a umidade");
        assertTrue(body.contains("115.0mm") || body.contains("115,0mm"), "O corpo deve conter a precipitação");
        assertTrue(body.contains("Chuva torrencial de 115mm detectada em 1 hora."));

        // Verifica envio de push via Firebase
        verify(firebaseService, times(1)).sendPushNotification(
                eq("TOKEN_DO_DISPOSITIVO_AQUI"),
                eq("🚨 Chuva Extrema"),
                eq("Chuva torrencial de 115mm detectada em 1 hora.")
        );
    }

    @Test
    void shouldHandleAlertWithoutEstadoCorrectly() {
        AlertResult alert = new AlertResult(
                true,
                AlertType.CRITICAL_FIRE_RISK,
                "Cuiabá",
                null,
                "Focos múltiplos de incêndio próximos à área de preservação.",
                38.5,
                18.0,
                0.0
        );

        consumer.receiveAlert(alert);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAlertEmail(org.mockito.ArgumentMatchers.anyString(), bodyCaptor.capture());

        assertTrue(bodyCaptor.getValue().contains("Local: Cuiabá"));
    }
}
