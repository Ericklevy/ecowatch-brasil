package br.com.ecowatch.alert_service.infrastructure.messaging;

import br.com.ecowatch.alert_service.application.AnalyzeWeatherReadingUseCase;
import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.infrastructure.cache.WeatherCacheService;
import br.com.ecowatch.alert_service.infrastructure.persistence.AlertEntity;
import br.com.ecowatch.alert_service.infrastructure.persistence.AlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherReadingConsumer {

    private final AnalyzeWeatherReadingUseCase useCase;
    private final ObjectMapper objectMapper;
    private final AlertRepository alertRepository;
    private final WeatherCacheService cacheService;
    private final RabbitTemplate rabbitTemplate;

    @KafkaListener(topics = "weather-readings", groupId = "alert-group")
    public void consume(String message) {
        try {
            WeatherReading reading = objectMapper.readValue(message, WeatherReading.class);
            log.info("📡 Nova leitura recebida de {}: {}°C", reading.municipio(), reading.temperatura());

            List<AlertResult> alerts = useCase.execute(reading);

            if (!alerts.isEmpty()) {
                log.warn("🚨 {} ALERTA(S) DISPARADO(S)!", alerts.size());
                for (AlertResult alerta : alerts) {
                    log.warn("   -> {}", alerta.message());

                    // Salva no banco
                    AlertEntity entity = new AlertEntity(
                            reading.municipio(),
                            reading.estado(),
                            alerta.type(),
                            alerta.message(),
                            reading.timestamp()
                    );
                    alertRepository.save(entity);

                    // Publica no RabbitMQ (JSON)
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.ALERT_EXCHANGE,
                            "alert." + alerta.type().name().toLowerCase(),
                            alerta
                    );
                    log.info("📨 Alerta publicado no RabbitMQ: {}", alerta.type());
                }
                log.info("💾 Alertas salvos no banco e publicados no RabbitMQ!");
            } else {
                log.info("✅ Clima normal. Nenhum alerta gerado.");
            }

            // Salva no cache Redis
            cacheService.saveLastReading(reading);
            log.info("💾 Leitura de {} armazenada em cache (Redis)", reading.municipio());

        } catch (Exception e) {
            log.error("❌ Erro ao processar mensagem do Kafka: {}", e.getMessage(), e);
        }
    }
}