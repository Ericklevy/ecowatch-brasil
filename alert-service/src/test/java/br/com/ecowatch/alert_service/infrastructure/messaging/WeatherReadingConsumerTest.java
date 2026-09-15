package br.com.ecowatch.alert_service.infrastructure.messaging;

import br.com.ecowatch.alert_service.application.AnalyzeWeatherReadingUseCase;
import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.infrastructure.cache.WeatherCacheService;
import br.com.ecowatch.alert_service.infrastructure.persistence.AlertEntity;
import br.com.ecowatch.alert_service.infrastructure.persistence.AlertRepository;
import br.com.ecowatch.alert_service.infrastructure.persistence.PrevisaoEntity;
import br.com.ecowatch.alert_service.infrastructure.persistence.PrevisaoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherReadingConsumerTest {

    @Mock
    private AnalyzeWeatherReadingUseCase useCase;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private PrevisaoRepository previsaoRepository;

    @Mock
    private WeatherCacheService cacheService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private WeatherReadingConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new WeatherReadingConsumer(
                useCase,
                objectMapper,
                alertRepository,
                previsaoRepository,
                cacheService,
                rabbitTemplate
        );
    }

    @Test
    void shouldSavePrevisaoAndAlertWhenConsumingMessage() throws Exception {
        WeatherReading reading = WeatherReading.builder()
                .municipio("Brasília")
                .estado("DF")
                .temperatura(35.0)
                .umidade(15.0)
                .precipitacao(0.0)
                .timestamp(LocalDateTime.now())
                .build();

        String json = objectMapper.writeValueAsString(reading);

        AlertResult alertResult = new AlertResult(
                true,
                AlertType.CRITICAL_FIRE_RISK,
                "brasilia",
                "DF",
                "Risco alto de incêndio",
                35.0,
                15.0,
                0.0
        );

        when(useCase.execute(any(WeatherReading.class))).thenReturn(List.of(alertResult));

        consumer.consume(json);

        // Verifica que salvou na tabela de previsões com nome normalizado 'brasilia'
        ArgumentCaptor<PrevisaoEntity> previsaoCaptor = ArgumentCaptor.forClass(PrevisaoEntity.class);
        verify(previsaoRepository).save(previsaoCaptor.capture());
        assertEquals("brasilia", previsaoCaptor.getValue().getMunicipio());
        assertEquals(35.0, previsaoCaptor.getValue().getTemperaturaPrevista());

        // Verifica que salvou na tabela de alertas
        ArgumentCaptor<AlertEntity> alertCaptor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertEquals("brasilia", alertCaptor.getValue().getMunicipio());
        assertEquals(AlertType.CRITICAL_FIRE_RISK, alertCaptor.getValue().getTipoAlerta());
    }
}
