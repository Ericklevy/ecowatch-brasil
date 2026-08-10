package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DengueOutbreakPolicyTest {

    // A classe ainda não existe no main, então ficará vermelha
    private final AlertPolicy policy = new DengueOutbreakPolicy();

    @Test
    void shouldTriggerAlertWhenHeatAndHumidityAreHigh() {
        // Arrange: Cenário ideal para o mosquito (Calor de 28°C e umidade de 85%)
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .temperatura(28.0)
                .umidade(85.0)
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        AlertResult result = policy.evaluate(reading);

        // Assert
        assertTrue(result.isAlert());
        assertEquals(AlertType.DENGUE_OUTBREAK_RISK, result.type());
        assertEquals("Brasília", result.municipio());
    }

    @Test
    void shouldNotTriggerAlertWhenOnlyTemperatureIsHigh() {
        // Arrange: Muito calor (30°C), mas clima extremamente seco (umidade 30%)
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .temperatura(30.0)
                .umidade(30.0)
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        AlertResult result = policy.evaluate(reading);

        // Assert: A umidade está baixa, então não deve gerar alerta de dengue
        assertFalse(result.isAlert());
    }
}