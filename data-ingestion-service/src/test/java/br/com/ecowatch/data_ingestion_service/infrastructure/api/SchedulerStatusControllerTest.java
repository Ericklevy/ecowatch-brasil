package br.com.ecowatch.data_ingestion_service.infrastructure.api;

import br.com.ecowatch.data_ingestion_service.application.WeatherIngestionScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerStatusControllerTest {

    @Mock
    private WeatherIngestionScheduler scheduler;

    @InjectMocks
    private SchedulerStatusController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "cronExpression", "0 0 */3 * * *");
        ReflectionTestUtils.setField(controller, "cities", List.of("Brasilia", "Manaus", "Recife"));

        when(scheduler.getLastExecutionTime()).thenReturn(new AtomicReference<>(LocalDateTime.of(2026, 11, 22, 4, 0)));
        when(scheduler.getLastExecutionStatus()).thenReturn(new AtomicReference<>("SUCESSO"));
        when(scheduler.getTotalCyclesExecuted()).thenReturn(new AtomicInteger(10));
        when(scheduler.getTotalCitiesSuccess()).thenReturn(new AtomicInteger(270));
        when(scheduler.getTotalCitiesFailed()).thenReturn(new AtomicInteger(0));
    }

    @Test
    void shouldReturnDetailedSchedulerStatus() {
        ResponseEntity<Map<String, Object>> response = controller.getStatus();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals("0 0 */3 * * *", body.get("cronExpression"));
        assertEquals(3, body.get("totalCidades"));
        assertEquals("SUCESSO", body.get("statusUltimaExecucao"));
        assertTrue(body.containsKey("ultimaExecucao"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> contadores = (Map<String, Integer>) body.get("contadores");
        assertNotNull(contadores);
        assertEquals(10, contadores.get("ciclosExecutados"));
        assertEquals(270, contadores.get("cidadesSucesso"));
        assertEquals(0, contadores.get("cidadesFalha"));
    }
}
