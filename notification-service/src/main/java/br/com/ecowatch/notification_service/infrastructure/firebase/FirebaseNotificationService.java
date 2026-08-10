package br.com.ecowatch.notification_service.infrastructure.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseNotificationService {

    private final FirebaseMessaging firebaseMessaging;

    public void sendPushNotification(String token, String title, String body) {
        log.info("📲 Tentando enviar push para token: {}", token);
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.info("✅ Push enviado com sucesso: {}", response);
        } catch (Exception e) {
            log.error("❌ Erro ao enviar push: {}", e.getMessage());
        }
    }
}