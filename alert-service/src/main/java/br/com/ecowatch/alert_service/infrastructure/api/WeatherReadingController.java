package br.com.ecowatch.alert_service.infrastructure.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@Tag(name = "Simulador de Sensores", description = "Endpoints para injetar dados de clima manualmente e testar as regras de alertas.")
public class WeatherReadingController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public WeatherReadingController(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Enviar leitura climática simulada", description = "Recebe um payload JSON com dados de clima e publica no Kafka (tópico: weather-readings) para que o motor de alertas processe e gere alertas se necessário.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Leitura enviada para o Kafka com sucesso"),
            @ApiResponse(responseCode = "400", description = "JSON da leitura é inválido ou malformado")
    })
    @PostMapping
    public ResponseEntity<String> simulateSensorReading(
            @Parameter(description = "Payload JSON da leitura climática", required = true, examples = {
                    @ExampleObject(name = "Onda de Calor", summary = "Simular onda de calor (Alerta)", value = "{\n  \"municipio\": \"Brasília\",\n  \"estado\": \"DF\",\n  \"temperatura\": 36.5,\n  \"umidade\": 40.0,\n  \"precipitacao\": 0.0,\n  \"timestamp\": \"2023-10-25T14:30:00\"\n}"),
                    @ExampleObject(name = "Chuva Forte", summary = "Simular chuva extrema (Alerta)", value = "{\n  \"municipio\": \"São Paulo\",\n  \"estado\": \"SP\",\n  \"temperatura\": 22.0,\n  \"umidade\": 95.0,\n  \"precipitacao\": 85.0,\n  \"timestamp\": \"2023-10-25T14:30:00\"\n}"),
                    @ExampleObject(name = "Normal", summary = "Clima sem anomalias (Sem Alerta)", value = "{\n  \"municipio\": \"Curitiba\",\n  \"estado\": \"PR\",\n  \"temperatura\": 24.0,\n  \"umidade\": 60.0,\n  \"precipitacao\": 0.0,\n  \"timestamp\": \"2023-10-25T14:30:00\"\n}")
            })
            @RequestBody String readingJson) {
        try {
            WeatherReading reading = objectMapper.readValue(readingJson, WeatherReading.class);
            if (reading.municipio() == null || reading.municipio().isBlank()) {
                return ResponseEntity.badRequest().body("Campo 'municipio' é obrigatório.");
            }
            String validJson = objectMapper.writeValueAsString(reading);
            kafkaTemplate.send("weather-readings", validJson);
            return ResponseEntity.ok("Leitura climática enviada para processamento com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("JSON inválido: " + e.getMessage());
        }
    }
}