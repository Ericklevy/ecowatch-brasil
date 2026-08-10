package br.com.ecowatch.alert_service.application;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.domain.policy.AlertPolicy;
import br.com.ecowatch.alert_service.domain.policy.DengueOutbreakPolicy;
import br.com.ecowatch.alert_service.domain.policy.ExtremeRainPolicy;
import br.com.ecowatch.alert_service.domain.policy.FireRiskPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeWeatherReadingUseCaseTest {

    // Instanciamos o caso de uso passando a lista de todas as nossas políticas
    // A classe AnalyzeWeatherReadingUseCase vai ficar vermelha, e é o esperado!
    private final List<AlertPolicy> policies = List.of(
            new ExtremeRainPolicy(),
            new DengueOutbreakPolicy(),
            new FireRiskPolicy()
    );

    private final AnalyzeWeatherReadingUseCase useCase = new AnalyzeWeatherReadingUseCase(policies);

    @Test
    void shouldReturnMultipleAlertsWhenConditionsAreMet() {
        // Arrange: Um cenário caótico (Muita chuva e muito calor - ativando Dengue e Chuva Extrema)
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .temperatura(28.0)
                .umidade(85.0) // Ativa o mosquito da Dengue
                .precipitacao(90.0) // Ativa a Chuva Extrema
                .timestamp(LocalDateTime.now())
                .build();

        // Act: Executamos o orquestrador
        List<AlertResult> alerts = useCase.execute(reading);

        // Assert: Devemos ter exatamente 2 alertas gerados na lista
        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().anyMatch(a -> a.type() == AlertType.EXTREME_RAIN));
        assertTrue(alerts.stream().anyMatch(a -> a.type() == AlertType.DENGUE_OUTBREAK_RISK));
    }

    @Test
    void shouldReturnEmptyListWhenNoConditionsAreMet() {
        // Arrange: Clima super agradável e normal
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .temperatura(22.0)
                .umidade(50.0)
                .precipitacao(0.0)
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        List<AlertResult> alerts = useCase.execute(reading);

        // Assert: A lista de alertas deve estar vazia (nenhuma anomalia)
        assertTrue(alerts.isEmpty());
    }
}