package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExtremeRainPolicyTest {

    // Instanciamos a política que vamos testar (vai dar erro de compilação por enquanto)
    private final AlertPolicy policy = new ExtremeRainPolicy();

    @Test
    void shouldTriggerAlertWhenRainIsAbove80mm() {
        // Arrange: Preparamos o cenário usando o nosso Builder (chuva de 85mm)
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .precipitacao(85.0)
                .timestamp(LocalDateTime.now())
                .build();

        // Act: Executamos a regra
        AlertResult result = policy.evaluate(reading);

        // Assert: Verificamos se o alerta foi gerado corretamente
        assertTrue(result.isAlert());
        assertEquals(AlertType.EXTREME_RAIN, result.type());
        assertEquals("Brasília", result.municipio());
    }

    @Test
    void shouldNotTriggerAlertWhenRainIsNormal() {
        // Arrange: Cenário de chuva normal (20mm)
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .precipitacao(20.0)
                .timestamp(LocalDateTime.now())
                .build();

        // Act: Executamos a regra
        AlertResult result = policy.evaluate(reading);

        // Assert: Garantimos que NENHUM alerta foi gerado
        assertFalse(result.isAlert());
    }
}