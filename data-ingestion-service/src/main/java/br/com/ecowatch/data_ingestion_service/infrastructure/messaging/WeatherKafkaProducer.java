package br.com.ecowatch.data_ingestion_service.infrastructure.messaging;

import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(WeatherReading reading) {
        try {
            String json = objectMapper.writeValueAsString(reading);
            kafkaTemplate.send("weather-readings", json);
            log.info("📤 Leitura de {} enviada para o Kafka", reading.municipio());
        } catch (Exception e) {
            log.error("Erro ao serializar e enviar leitura: {}", e.getMessage());
        }
    }
}