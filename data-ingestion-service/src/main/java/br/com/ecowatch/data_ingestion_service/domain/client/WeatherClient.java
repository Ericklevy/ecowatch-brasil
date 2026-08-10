package br.com.ecowatch.data_ingestion_service.domain.client;

import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;

/**
 * Contrato para qualquer cliente que busque dados climáticos.
 * Permite múltiplas implementações (INMET, Open-Meteo, OpenWeatherMap, etc.)
 */
public interface WeatherClient {

    /**
     * Busca dados climáticos para uma cidade.
     * @param city nome da cidade (ex: "Brasilia")
     * @return WeatherReading com os dados, ou null se falhar
     */
    WeatherReading fetchWeather(String city);

    /**
     * Nome da fonte (ex: "INMET", "Open-Meteo")
     */
    default String getSourceName() {
        return this.getClass().getSimpleName().replace("Client", "");
    }
}
