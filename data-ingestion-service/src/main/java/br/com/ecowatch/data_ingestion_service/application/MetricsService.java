package br.com.ecowatch.data_ingestion_service.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Declaração das variáveis (sem inicialização)
    private Counter nasaCalls;
    private Counter nasaErrors;
    private Counter openMeteoCalls;
    private Counter openMeteoErrors;
    private Timer nasaTimer;
    private Timer openMeteoTimer;

    @PostConstruct
    public void init() {
        // Inicialização das métricas após a injeção do meterRegistry
        nasaCalls = Counter.builder("weather.api.calls")
                .tag("source", "nasa")
                .description("Total de chamadas à API NASA POWER")
                .register(meterRegistry);

        nasaErrors = Counter.builder("weather.api.errors")
                .tag("source", "nasa")
                .description("Total de erros na API NASA POWER")
                .register(meterRegistry);

        openMeteoCalls = Counter.builder("weather.api.calls")
                .tag("source", "openmeteo")
                .description("Total de chamadas à API Open-Meteo")
                .register(meterRegistry);

        openMeteoErrors = Counter.builder("weather.api.errors")
                .tag("source", "openmeteo")
                .description("Total de erros na API Open-Meteo")
                .register(meterRegistry);

        nasaTimer = Timer.builder("weather.api.duration")
                .tag("source", "nasa")
                .description("Tempo de resposta da NASA POWER")
                .register(meterRegistry);

        openMeteoTimer = Timer.builder("weather.api.duration")
                .tag("source", "openmeteo")
                .description("Tempo de resposta da Open-Meteo")
                .register(meterRegistry);

        log.info("✅ Métricas do Micrometer inicializadas com sucesso!");
    }

    // Métodos para registrar métricas
    public void recordNasaSuccess(long durationMs) {
        nasaCalls.increment();
        nasaTimer.record(Duration.ofMillis(durationMs));
    }

    public void recordNasaError() {
        nasaErrors.increment();
    }

    public void recordOpenMeteoSuccess(long durationMs) {
        openMeteoCalls.increment();
        openMeteoTimer.record(Duration.ofMillis(durationMs));
    }

    public void recordOpenMeteoError() {
        openMeteoErrors.increment();
    }
}