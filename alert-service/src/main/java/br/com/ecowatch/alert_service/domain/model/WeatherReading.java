package br.com.ecowatch.alert_service.domain.model;

import lombok.Builder;
import java.time.LocalDateTime;

/**
 * Modelo central de leitura climática recebido do Kafka.
 * Deve estar em sincronia com o WeatherReading do data-ingestion-service.
 */
@Builder(toBuilder = true)
public record WeatherReading(
        // --- Dados Climáticos Básicos ---
        String municipio,
        String estado,
        double temperatura,
        double umidade,
        double precipitacao,
        LocalDateTime timestamp,

        // --- Qualidade do Ar (Open-Meteo Air Quality API) ---
        double pm25,
        double pm10,

        // --- Focos de Incêndio (INPE / BDQueimadas) ---
        int focosIncendio,

        // --- Nível dos Rios (ANA) ---
        double nivelRio,

        // --- Alertas de Enxurrada (CEMADEN) ---
        boolean alertaEnxurrada
) {}