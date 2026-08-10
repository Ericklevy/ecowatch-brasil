package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FireRiskPolicyTest {

    // A classe principal ainda não existe, então ficará vermelho
    private final AlertPolicy policy = new FireRiskPolicy();

    @Test
    void shouldTriggerAlertWhenHotAndDry() {
        // Arrange: Cenário de alto risco (32°C e umidade em 25%)
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .temperatura(32.0)
                .umidade(25.0)
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        AlertResult result = policy.evaluate(reading);

        // Assert
        assertTrue(result.isAlert());
        assertEquals(AlertType.CRITICAL_FIRE_RISK, result.type());
        assertEquals("Brasília", result.municipio());
    }

    @Test
    void shouldNotTriggerAlertWhenHumidityIsNormal() {
        // Arrange: Muito calor (35°C), mas umidade segura (50%)
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .temperatura(35.0)
                .umidade(50.0)
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        AlertResult result = policy.evaluate(reading);

        // Assert: A umidade está alta, então não deve gerar alerta de incêndio
        assertFalse(result.isAlert());
    }
}