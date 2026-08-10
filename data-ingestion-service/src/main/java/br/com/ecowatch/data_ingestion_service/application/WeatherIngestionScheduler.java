package br.com.ecowatch.data_ingestion_service.application;

import br.com.ecowatch.data_ingestion_service.domain.client.WeatherClient;
import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import br.com.ecowatch.data_ingestion_service.infrastructure.messaging.WeatherKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WeatherIngestionScheduler {

    private final WeatherClient primaryClient;

    @Qualifier("openMeteoClient")
    private final WeatherClient fallbackClient;
    private final WeatherKafkaProducer kafkaProducer;

    @Value("${monitor.cities}")
    private List<String> cities;

    public WeatherIngestionScheduler(
            WeatherClient primaryClient,
            @Qualifier("openMeteoClient") WeatherClient fallbackClient,
            WeatherKafkaProducer kafkaProducer) {
        this.primaryClient = primaryClient;
        this.fallbackClient = fallbackClient;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Ciclo de coleta: a cada 5 minutos tenta obter dados da fonte primária (NASA POWER)
     * e, se falhar, usa o fallback (Open-Meteo).
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 10000)
    public void fetchAndPublishWeather() {
        log.info("⏰ Iniciando ciclo de coleta de dados climáticos...");
        log.info("📋 Lista de cidades: {}", cities);

        for (String city : cities) {
            String cityTrimmed = city.trim();
            WeatherReading reading = null;

            // 1. Tenta com a fonte primária (NASA POWER)
            try {
                reading = primaryClient.fetchWeather(cityTrimmed);
                if (reading != null) {
                    log.info("✅ [{}] Dados obtidos via {}", cityTrimmed, primaryClient.getSourceName());
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
                        log.info("✅ [{}] Dados obtidos via fallback ({})", cityTrimmed, fallbackClient.getSourceName());
                    } else {
                        log.warn("❌ [{}] Falha também no fallback", cityTrimmed);
                    }
                } catch (Exception e) {
                    log.error("❌ [{}] Erro no fallback: {}", cityTrimmed, e.getMessage());
                }
            }

            // 3. Se obteve dados, publica no Kafka
            if (reading != null) {
                kafkaProducer.send(reading);
            } else {
                log.error("❌ [{}] Não foi possível obter dados de nenhuma fonte.", cityTrimmed);
            }
        }

        log.info("✅ Ciclo de coleta finalizado.");
    }
}