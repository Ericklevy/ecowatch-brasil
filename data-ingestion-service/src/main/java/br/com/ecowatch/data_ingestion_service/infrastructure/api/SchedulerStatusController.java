package br.com.ecowatch.data_ingestion_service.infrastructure.api;

import br.com.ecowatch.data_ingestion_service.application.WeatherIngestionScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller REST para monitoramento e controle do agendador automático.
 */
@Slf4j
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Tag(name = "Controle de Agendamento", description = "Endpoints para verificar o status e acionar manualmente o ciclo de ingestão de dados.")
public class SchedulerStatusController {

    private final WeatherIngestionScheduler scheduler;

    @Value("${scheduler.cron:0 0 * * * *}")
    private String cronExpression;

    @Value("${monitor.cities}")
    private List<String> cities;

    @Operation(summary = "Obter status do agendador", description = "Retorna um relatório detalhado com a última execução, expressão cron atual e contadores de falhas/sucessos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status recuperado com sucesso")
    })
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Configuração
        status.put("cronExpression", cronExpression);
        status.put("cidadesMonitoradas", cities);
        status.put("totalCidades", cities.size());

        // Estado atual
        LocalDateTime lastExec = scheduler.getLastExecutionTime().get();
        status.put("ultimaExecucao", lastExec != null ? lastExec.toString() : "Nunca executado");
        status.put("statusUltimaExecucao", scheduler.getLastExecutionStatus().get());

        // Contadores acumulados
        Map<String, Integer> counters = new LinkedHashMap<>();
        counters.put("ciclosExecutados", scheduler.getTotalCyclesExecuted().get());
        counters.put("cidadesSucesso", scheduler.getTotalCitiesSuccess().get());
        counters.put("cidadesFalha", scheduler.getTotalCitiesFailed().get());
        status.put("contadores", counters);

        return ResponseEntity.ok(status);
    }

    private final java.util.concurrent.atomic.AtomicBoolean isCollecting = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Operation(summary = "Disparar coleta manual", description = "Força uma coleta de dados imediata e assíncrona, independentemente do cronograma.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Coleta iniciada em background com sucesso"),
            @ApiResponse(responseCode = "429", description = "Já existe uma coleta em andamento")
    })
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerManual() {
        if (!isCollecting.compareAndSet(false, true)) {
            log.warn("⚠️ Tentativa de disparo ignorada: já existe uma coleta em execução.");
            Map<String, String> busyResponse = new LinkedHashMap<>();
            busyResponse.put("status", "OCUPADO");
            busyResponse.put("mensagem", "Uma coleta já está em andamento no momento. Aguarde a finalização antes de tentar novamente.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body(busyResponse);
        }

        log.info("🔧 Coleta manual disparada via API REST");

        // Executa em thread separada para não bloquear a resposta HTTP
        new Thread(() -> {
            try {
                scheduler.fetchAndPublishWeather();
            } catch (Exception e) {
                log.error("❌ Erro na coleta manual: {}", e.getMessage(), e);
            } finally {
                isCollecting.set(false);
            }
        }, "manual-ingestion-trigger").start();

        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "DISPARADO");
        response.put("mensagem", "Coleta manual iniciada em background. Consulte GET /api/scheduler/status para acompanhar.");

        return ResponseEntity.accepted().body(response);
    }
}
