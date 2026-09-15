package br.com.ecowatch.data_ingestion_service.domain.model;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Modelo central de leitura climática que trafega pelo Kafka.
 * Consolida dados de múltiplas fontes antes de publicar no tópico weather-readings.
 */
@Builder(toBuilder = true)
public record WeatherReading(
        // --- Dados Climáticos Básicos (NASA POWER / Open-Meteo) ---
        String municipio,
        String estado,
        double temperatura,
        double umidade,
        double precipitacao,
        LocalDateTime timestamp,

        // --- Qualidade do Ar (Open-Meteo Air Quality API) ---
        double pm25,        // Partículas finas PM2.5 (µg/m³) — risco respiratório acima de 50
        double pm10,        // Partículas grossas PM10 (µg/m³) — risco respiratório acima de 100

        // --- Focos de Incêndio (INPE / BDQueimadas) ---
        int focosIncendio,  // Número de focos de calor detectados num raio de 50km

        // --- Nível dos Rios (ANA - Agência Nacional de Águas) ---
        double nivelRio,    // Cota do rio em metros (0.0 = sem dados / cidade sem rio monitorado)

        // --- Alertas de Enxurrada (CEMADEN) ---
        boolean alertaEnxurrada  // true se o CEMADEN emitiu alerta de enxurrada para o município
) {
    /** Construtor compacto com defaults para os campos opcionais (retrocompatibilidade). */
    public static WeatherReading basico(String municipio, String estado,
                                        double temperatura, double umidade,
                                        double precipitacao, LocalDateTime timestamp) {
        return WeatherReading.builder()
                .municipio(municipio)
                .estado(estado)
                .temperatura(temperatura)
                .umidade(umidade)
                .precipitacao(precipitacao)
                .timestamp(timestamp)
                .pm25(0.0)
                .pm10(0.0)
                .focosIncendio(0)
                .nivelRio(0.0)
                .alertaEnxurrada(false)
                .build();
    }
}