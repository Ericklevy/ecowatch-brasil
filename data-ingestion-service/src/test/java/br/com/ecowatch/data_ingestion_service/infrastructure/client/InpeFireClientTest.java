package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InpeFireClientTest {

    private InpeFireClient inpeFireClient;

    @BeforeEach
    void setUp() {
        inpeFireClient = new InpeFireClient("SIMULADO");
    }

    @Test
    void shouldEstimateFiresWhenClimateConditionsAreCriticalInCuiaba() {
        // Cuiabá no Cerrado/Pantanal: 39°C, umidade 15%, sem chuva
        WeatherReading dryReading = WeatherReading.builder()
                .municipio("Cuiaba")
                .estado("MT")
                .temperatura(39.0)
                .umidade(15.0)
                .precipitacao(0.0)
                .timestamp(LocalDateTime.now())
                .build();

        WeatherReading enriched = inpeFireClient.enrichWithFireData(dryReading);

        assertTrue(enriched.focosIncendio() > 0, "Deveria estimar focos de incêndio com clima extremamente seco e quente");
    }

    @Test
    void shouldEstimateZeroFiresWhenRainIsPresentAndHumidityIsHigh() {
        // Porto Alegre chovendo forte, umidade 95%, temperatura 18°C
        WeatherReading wetReading = WeatherReading.builder()
                .municipio("Porto Alegre")
                .estado("RS")
                .temperatura(18.0)
                .umidade(95.0)
                .precipitacao(35.0)
                .timestamp(LocalDateTime.now())
                .build();

        WeatherReading enriched = inpeFireClient.enrichWithFireData(wetReading);

        assertEquals(0, enriched.focosIncendio(), "Não deve estimar focos com chuva ativa e alta umidade");
    }

    @Test
    void shouldEstimateFiresInAmazonBiomeWithModerateHumidity() {
        // Manaus na Amazônia: 36°C, umidade 45%, sem chuva
        WeatherReading amazonReading = WeatherReading.builder()
                .municipio("Manaus")
                .estado("AM")
                .temperatura(36.0)
                .umidade(45.0)
                .precipitacao(0.0)
                .timestamp(LocalDateTime.now())
                .build();

        WeatherReading enriched = inpeFireClient.enrichWithFireData(amazonReading);

        assertTrue(enriched.focosIncendio() > 0, "No bioma Amazônia, calor forte sem chuva deve acusar focos");
    }
}
