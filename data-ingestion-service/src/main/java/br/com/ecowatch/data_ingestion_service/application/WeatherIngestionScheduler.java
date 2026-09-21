package br.com.ecowatch.data_ingestion_service.application;

import br.com.ecowatch.data_ingestion_service.domain.client.WeatherClient;
import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.AnaRiverClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.CemadenClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.InpeFireClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.client.OpenMeteoAirQualityClient;
import br.com.ecowatch.data_ingestion_service.infrastructure.messaging.WeatherKafkaProducer;
import br.com.ecowatch.data_ingestion_service.util.CityNameNormalizer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agendador automático de coleta de dados climáticos multi-fonte.
 *
 * <p>Para cada cidade, o ciclo executa em 2 fases:
 * <ol>
 *   <li><strong>Clima base:</strong> NASA POWER (primária) → Open-Meteo (fallback)</li>
 *   <li><strong>Enriquecimento:</strong> Open-Meteo Air Quality → INPE → CEMADEN → ANA</li>
 * </ol>
 * O resultado consolidado é publicado no tópico Kafka {@code weather-readings}.</p>
 */
@Slf4j
@Service
public class WeatherIngestionScheduler {

    private final WeatherClient primaryClient;
    private final WeatherClient fallbackClient;
    private final WeatherKafkaProducer kafkaProducer;
    private final MetricsService metricsService;

    // --- Clients de Enriquecimento ---
    private final OpenMeteoAirQualityClient airQualityClient;
    private final InpeFireClient inpeFireClient;
    private final CemadenClient cemadenClient;
    private final AnaRiverClient anaRiverClient;

    @Value("${monitor.cities}")
    private List<String> cities;

    // --- Estado observável (para endpoint de status) ---
    @Getter private final AtomicReference<LocalDateTime> lastExecutionTime = new AtomicReference<>();
    @Getter private final AtomicReference<String> lastExecutionStatus = new AtomicReference<>("NUNCA_EXECUTADO");
    @Getter private final AtomicInteger totalCyclesExecuted = new AtomicInteger(0);
    @Getter private final AtomicInteger totalCitiesSuccess = new AtomicInteger(0);
    @Getter private final AtomicInteger totalCitiesFailed = new AtomicInteger(0);

    public WeatherIngestionScheduler(
            WeatherClient primaryClient,
            @Qualifier("openMeteoClient") WeatherClient fallbackClient,
            WeatherKafkaProducer kafkaProducer,
            MetricsService metricsService,
            OpenMeteoAirQualityClient airQualityClient,
            InpeFireClient inpeFireClient,
            CemadenClient cemadenClient,
            AnaRiverClient anaRiverClient) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.kafkaProducer = kafkaProducer;
        this.metricsService = metricsService;
        this.airQualityClient = airQualityClient;
        this.inpeFireClient = inpeFireClient;
        this.cemadenClient = cemadenClient;
        this.anaRiverClient = anaRiverClient;
    }

    /**
     * Executa uma coleta inicial ao iniciar a aplicação (após 15s de delay)
     * para garantir que dados estejam disponíveis imediatamente.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("🚀 Aplicação iniciada! Agendando coleta inicial em 15 segundos...");
        try {
            Thread.sleep(15_000); // Aguarda infraestrutura (Kafka, etc.) estar pronta
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        log.info("🔄 Executando coleta inicial de dados climáticos...");
        fetchAndPublishWeather();
    }

    /**
     * Ciclo automático de coleta de dados climáticos via CRON.
     *
     * <p>Expressão cron configurável via propriedade {@code scheduler.cron}.
     * Padrão: {@code 0 0 * * * *} (a cada hora cheia).</p>
     *
     * <p>Fluxo para cada cidade:
     * <ol>
     *   <li>Tenta fonte primária (NASA POWER)</li>
     *   <li>Se falhar, tenta fallback (Open-Meteo)</li>
     *   <li>Normaliza nome da cidade e publica no Kafka</li>
     * </ol>
     * </p>
     */
    @Scheduled(cron = "${scheduler.cron:0 0 * * * *}")
    public void fetchAndPublishWeather() {
        LocalDateTime inicio = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        int successCount = 0;
        int failCount = 0;

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("⏰ CICLO DE COLETA AUTOMÁTICA - Início: {}", inicio);
        log.info("📋 Cidades monitoradas ({}): {}", cities.size(), cities);
        log.info("🔗 Fonte primária: {} | Fallback: {}", primaryClient.getSourceName(), fallbackClient.getSourceName());
        log.info("═══════════════════════════════════════════════════════════════");

        for (String city : cities) {
            String cityTrimmed = city.trim();
            WeatherReading reading = null;
            String sourceUsed = "nenhuma";

            // 1. Tenta com a fonte primária (NASA POWER)
            try {
                reading = primaryClient.fetchWeather(cityTrimmed);
                if (reading != null) {
                    sourceUsed = primaryClient.getSourceName();
                    log.info("✅ [{}] Dados obtidos via {}", cityTrimmed, sourceUsed);
                } else {
                    log.warn("⚠️ [{}] Falha no {}, tentando fallback...", cityTrimmed, primaryClient.getSourceName());
                }
            } catch (Exception e) {
                log.error("❌ [{}] Erro no {}: {}", cityTrimmed, primaryClient.getSourceName(), e.getMessage());
            }

            // 2. Se falhou, usa fallback (Open-Meteo)
            if (reading == null) {
                try {
                    reading = fallbackClient.fetchWeather(cityTrimmed);
                    if (reading != null) {
                        sourceUsed = fallbackClient.getSourceName();
                        log.info("✅ [{}] Dados obtidos via fallback ({})", cityTrimmed, sourceUsed);
                    } else {
                        log.warn("❌ [{}] Falha também no fallback", cityTrimmed);
                    }
                } catch (Exception e) {
                    log.error("❌ [{}] Erro no fallback: {}", cityTrimmed, e.getMessage());
                }
            }

            // 3. Se obteve dados, enriquece com APIs públicas e publica no Kafka
            if (reading != null) {
                String municipioNormalizado = CityNameNormalizer.normalize(reading.municipio());

                // Fase 2: Enriquecimento multi-fonte (cada client é tolerante a falhas)
                log.info("🔬 [{}] Iniciando enriquecimento com APIs públicas...", cityTrimmed);
                WeatherReading enriched = WeatherReading.builder()
                        .municipio(municipioNormalizado)
                        .estado(reading.estado())
                        .temperatura(reading.temperatura())
                        .umidade(reading.umidade())
                        .precipitacao(reading.precipitacao())
                        .timestamp(reading.timestamp())
                        .pm25(0.0).pm10(0.0).focosIncendio(0).nivelRio(0.0).alertaEnxurrada(false)
                        .build();

                // 3a. Qualidade do Ar (Open-Meteo Air Quality — API real, gratuita)
                enriched = airQualityClient.enrichWithAirQuality(enriched);

                // 3b. Focos de Incêndio (INPE — estimativa ou API real se configurada)
                enriched = inpeFireClient.enrichWithFireData(enriched);

                // 3c. Alerta de Enxurrada (CEMADEN — estimativa ou API real se configurada)
                enriched = cemadenClient.enrichWithFloodAlert(enriched);

                // 3d. Nível do Rio (ANA — apenas cidades ribeirinhas monitoradas)
                enriched = anaRiverClient.enrichWithRiverLevel(enriched);

                kafkaProducer.send(enriched);
                successCount++;
                log.info("📤 [{}] Dados completos publicados no Kafka (fonte: {} | PM2.5={} | focos={} | nível_rio={}m)",
                        municipioNormalizado, sourceUsed,
                        enriched.pm25(), enriched.focosIncendio(), enriched.nivelRio());
            } else {
                failCount++;
                log.error("❌ [{}] Não foi possível obter dados de nenhuma fonte.", cityTrimmed);
            }
        }

        // --- Atualiza estado e métricas ---
        LocalDateTime fim = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        Duration duracao = Duration.between(inicio, fim);
        int cycleNumber = totalCyclesExecuted.incrementAndGet();
        totalCitiesSuccess.addAndGet(successCount);
        totalCitiesFailed.addAndGet(failCount);
        lastExecutionTime.set(fim);
        lastExecutionStatus.set(failCount == 0 ? "SUCESSO" : (successCount > 0 ? "PARCIAL" : "FALHA"));

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("✅ CICLO #{} FINALIZADO em {}s", cycleNumber, duracao.toSeconds());
        log.info("📊 Resultado: {}/{} cidades com sucesso | {} falhas",
                successCount, cities.size(), failCount);
        log.info("📈 Acumulado: {} ciclos | {} sucessos | {} falhas",
                cycleNumber, totalCitiesSuccess.get(), totalCitiesFailed.get());
        log.info("⏭️ Próxima execução: conforme cron configurado");
        log.info("═══════════════════════════════════════════════════════════════");
    }

    /**
     * Coleta dados sob demanda de uma única cidade e publica no Kafka.
     * Usado quando o frontend solicita uma cidade que ainda não está no cache.
     */
    public boolean fetchAndPublishSingleCity(String city) {
        String cityTrimmed = city.trim();
        log.info("🎯 [ON-DEMAND] Coleta sob demanda solicitada para: {}", cityTrimmed);

        WeatherReading reading = null;
        String sourceUsed = "nenhuma";

        // 1. Tenta fonte primária (NASA POWER)
        try {
            reading = primaryClient.fetchWeather(cityTrimmed);
            if (reading != null) {
                sourceUsed = primaryClient.getSourceName();
            }
        } catch (Exception e) {
            log.warn("⚠️ [ON-DEMAND] Falha no {}: {}", primaryClient.getSourceName(), e.getMessage());
        }

        // 2. Fallback (Open-Meteo)
        if (reading == null) {
            try {
                reading = fallbackClient.fetchWeather(cityTrimmed);
                if (reading != null) {
                    sourceUsed = fallbackClient.getSourceName();
                }
            } catch (Exception e) {
                log.error("❌ [ON-DEMAND] Falha no fallback: {}", e.getMessage());
            }
        }

        if (reading != null) {
            String municipioNormalizado = CityNameNormalizer.normalize(reading.municipio());
            WeatherReading enriched = WeatherReading.builder()
                    .municipio(municipioNormalizado)
                    .estado(reading.estado())
                    .temperatura(reading.temperatura())
                    .umidade(reading.umidade())
                    .precipitacao(reading.precipitacao())
                    .timestamp(reading.timestamp())
                    .pm25(0.0).pm10(0.0).focosIncendio(0).nivelRio(0.0).alertaEnxurrada(false)
                    .build();

            enriched = airQualityClient.enrichWithAirQuality(enriched);
            enriched = inpeFireClient.enrichWithFireData(enriched);
            enriched = cemadenClient.enrichWithFloodAlert(enriched);
            enriched = anaRiverClient.enrichWithRiverLevel(enriched);

            kafkaProducer.send(enriched);
            log.info("✅ [ON-DEMAND] Cidade {} coletada e enviada via Kafka (fonte: {})", municipioNormalizado, sourceUsed);
            return true;
        }

        log.warn("❌ [ON-DEMAND] Não foi possível obter dados para {}", cityTrimmed);
        return false;
    }
}