package br.com.ecowatch.alert_service.infrastructure.messaging;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AlertNotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.ALERT_QUEUE)
    public void receiveAlert(AlertResult alert) {
        // agora recebe um AlertResult desserializado
    }
}