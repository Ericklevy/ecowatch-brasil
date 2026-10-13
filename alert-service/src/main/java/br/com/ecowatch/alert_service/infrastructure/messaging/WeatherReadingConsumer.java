package br.com.ecowatch.alert_service.infrastructure.messaging;

import br.com.ecowatch.alert_service.application.AnalyzeWeatherReadingUseCase;
import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.infrastructure.cache.WeatherCacheService;
import br.com.ecowatch.alert_service.infrastructure.persistence.AlertEntity;
import br.com.ecowatch.alert_service.infrastructure.persistence.AlertRepository;
import br.com.ecowatch.alert_service.infrastructure.persistence.PrevisaoEntity;
import br.com.ecowatch.alert_service.infrastructure.persistence.PrevisaoRepository;
import br.com.ecowatch.alert_service.util.CityNameNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@Lazy(false)
@RequiredArgsConstructor
public class WeatherReadingConsumer {

    private final AnalyzeWeatherReadingUseCase useCase;
    private final ObjectMapper objectMapper;
    private final AlertRepository alertRepository;
    private final PrevisaoRepository previsaoRepository;
    private final WeatherCacheService cacheService;
    private final RabbitTemplate rabbitTemplate;

    @KafkaListener(topics = "weather-readings", groupId = "${spring.kafka.consumer.group-id:$Default}")
    public void consume(String message) {
        try {
            WeatherReading rawReading = objectMapper.readValue(message, WeatherReading.class);
            String municipioNormalizado = CityNameNormalizer.normalize(rawReading.municipio());
            
            WeatherReading reading = rawReading.toBuilder()
                    .municipio(municipioNormalizado)
                    .build();

            log.info("📡 Nova leitura recebida de {}: {}°C, {} focos de calor, nível rio: {}m",
                    reading.municipio(), reading.temperatura(), reading.focosIncendio(), reading.nivelRio());

            // 1. Salva a previsão na tabela 'previsoes'
            int riscoIncendio = (reading.focosIncendio() > 0 || (reading.temperatura() > 30 && reading.umidade() < 35)) ? 80 : 20;
            int riscoDengue = (reading.umidade() > 70 && reading.temperatura() > 25) ? 75 : 15;
            LocalDate dataPrevisao = reading.timestamp() != null ? reading.timestamp().toLocalDate() : LocalDate.now();

            PrevisaoEntity previsaoEntity = new PrevisaoEntity(
                    reading.municipio(),
                    dataPrevisao,
                    reading.temperatura(),
                    reading.precipitacao(),
                    reading.umidade(),
                    riscoIncendio,
                    riscoDengue
            );
            previsaoRepository.save(previsaoEntity);
            log.info("💾 Dados salvos na tabela 'previsoes' para {}", reading.municipio());

            // 2. Avalia e salva alertas
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

                    // Publica no RabbitMQ (se disponível)
                    try {
                        rabbitTemplate.convertAndSend(
                                RabbitMQConfig.ALERT_EXCHANGE,
                                "alert." + alerta.type().name().toLowerCase(),
                                alerta
                        );
                        log.info("📨 Alerta publicado no RabbitMQ: {}", alerta.type());
                    } catch (Exception ex) {
                        log.warn("⚠️ RabbitMQ indisponível, alerta mantido no banco: {}", ex.getMessage());
                    }
                }
                log.info("💾 Alertas salvos no banco!");
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