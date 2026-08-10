package br.com.ecowatch.alert_service.infrastructure.api;

import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class WeatherReadingController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public WeatherReadingController(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping
    public ResponseEntity<String> simulateSensorReading(@RequestBody String readingJson) {
        kafkaTemplate.send("weather-readings", readingJson);
        return ResponseEntity.ok("Leitura climática enviada para processamento com sucesso!");
    }
}