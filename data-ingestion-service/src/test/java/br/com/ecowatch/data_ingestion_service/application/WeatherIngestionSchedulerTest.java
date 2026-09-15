package br.com.ecowatch.data_ingestion_service.application;

import br.com.ecowatch.data_ingestion_service.domain.client.WeatherClient;
import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.AnaRiverClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.CemadenClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.InpeFireClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.OpenMeteoAirQualityClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.messaging.WeatherKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherIngestionSchedulerTest {

    @Mock private WeatherClient primaryClient;
    @Mock private WeatherClient fallbackClient;
    @Mock private WeatherKafkaProducer kafkaProducer;
    @Mock private MetricsService metricsService;

    // Novos clients de enriquecimento
    @Mock private OpenMeteoAirQualityClient airQualityClient;
    @Mock private InpeFireClient inpeFireClient;
    @Mock private CemadenClient cemadenClient;
    @Mock private AnaRiverClient anaRiverClient;

    private WeatherIngestionScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new WeatherIngestionScheduler(
                primaryClient, fallbackClient, kafkaProducer, metricsService,
                airQualityClient, inpeFireClient, cemadenClient, anaRiverClient);

        // Clients de enriquecimento como passthrough (lenient: só são chamados se o clima base for obtido)
        org.mockito.Mockito.lenient().when(airQualityClient.enrichWithAirQuality(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(inpeFireClient.enrichWithFireData(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(cemadenClient.enrichWithFloodAlert(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(anaRiverClient.enrichWithRiverLevel(any())).thenAnswer(inv -> inv.getArgument(0));


        Field citiesField = WeatherIngestionScheduler.class.getDeclaredField("cities");
        citiesField.setAccessible(true);
        citiesField.set(scheduler, List.of("Brasília"));
    }

    /** Helper para criar um WeatherReading completo (com os novos campos com defaults). */
    private WeatherReading buildReading(String municipio, String estado,
                                        double temp, double umidade, double prec) {
        return WeatherReading.builder()
                .municipio(municipio)
                .estado(estado)
                .temperatura(temp)
                .umidade(umidade)
                .precipitacao(prec)
                .timestamp(LocalDateTime.now())
                .pm25(0.0).pm10(0.0)
                .focosIncendio(0)
                .nivelRio(0.0)
                .alertaEnxurrada(false)
                .build();
    }

    @Test
    void shouldFetchAndPublishNormalizedReading() {
        WeatherReading rawReading = buildReading("Brasília", "DF", 27.5, 60.0, 0.0);

        when(primaryClient.fetchWeather("Brasília")).thenReturn(rawReading);

        scheduler.fetchAndPublishWeather();

        ArgumentCaptor<WeatherReading> captor = ArgumentCaptor.forClass(WeatherReading.class);
        verify(kafkaProducer).send(captor.capture());

        assertEquals("brasilia", captor.getValue().municipio());
        assertEquals("DF", captor.getValue().estado());
        assertEquals(27.5, captor.getValue().temperatura());
    }

    @Test
    void shouldTrackExecutionStatusAfterCycle() {
        WeatherReading rawReading = buildReading("Brasília", "DF", 30.0, 55.0, 2.0);

        when(primaryClient.fetchWeather("Brasília")).thenReturn(rawReading);

        scheduler.fetchAndPublishWeather();

        assertEquals(1, scheduler.getTotalCyclesExecuted().get());
        assertEquals(1, scheduler.getTotalCitiesSuccess().get());
        assertEquals(0, scheduler.getTotalCitiesFailed().get());
        assertEquals("SUCESSO", scheduler.getLastExecutionStatus().get());
        assertNotNull(scheduler.getLastExecutionTime().get());
    }

    @Test
    void shouldUseFallbackWhenPrimaryFails() {
        WeatherReading fallbackReading = buildReading("Brasília", "DF", 28.0, 65.0, 1.0);

        when(primaryClient.fetchWeather("Brasília")).thenReturn(null);
        when(fallbackClient.fetchWeather("Brasília")).thenReturn(fallbackReading);

        scheduler.fetchAndPublishWeather();

        ArgumentCaptor<WeatherReading> captor = ArgumentCaptor.forClass(WeatherReading.class);
        verify(kafkaProducer).send(captor.capture());

        assertEquals("brasilia", captor.getValue().municipio());
        assertEquals("SUCESSO", scheduler.getLastExecutionStatus().get());
    }

    @Test
    void shouldReportFailureWhenBothSourcesFail() {
        when(primaryClient.fetchWeather("Brasília")).thenReturn(null);
        when(fallbackClient.fetchWeather("Brasília")).thenReturn(null);

        scheduler.fetchAndPublishWeather();

        assertEquals(1, scheduler.getTotalCyclesExecuted().get());
        assertEquals(0, scheduler.getTotalCitiesSuccess().get());
        assertEquals(1, scheduler.getTotalCitiesFailed().get());
        assertEquals("FALHA", scheduler.getLastExecutionStatus().get());
    }
}
