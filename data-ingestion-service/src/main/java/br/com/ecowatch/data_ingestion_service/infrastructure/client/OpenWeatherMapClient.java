package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.client.WeatherClient;
import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
// Desativado – usando INMET (primário) e Open-Meteo (fallback)
public class OpenWeatherMapClient implements WeatherClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;

    public OpenWeatherMapClient(ObjectMapper objectMapper,
                                @Value("${api.openweather.key}") String apiKey,
                                @Value("${api.openweather.url}") String apiUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.restClient = RestClient.builder().build();
    }


    public WeatherReading fetchWeather(String city) {
        System.out.println("=========================================");
        System.out.println("🚀 PROCESSANDO CIDADE: " + city);
        System.out.println("=========================================");
        System.out.flush();
        log.info("🌐 INÍCIO da busca para: {}", city);
        log.info("🔥 fetchWeather chamado para: '{}'", city);
        try {
            String uri = String.format("%s?q=%s,BR&appid=%s&units=metric", apiUrl, city, apiKey);
            log.info("📡 URL: {}", uri);

            String jsonResponse = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            log.info("📄 JSON recebido (primeiros 200 chars): {}",
                    jsonResponse != null ? jsonResponse.substring(0, Math.min(200, jsonResponse.length())) : "null");

            JsonNode root = objectMapper.readTree(jsonResponse);

            // Verifica se a API retornou erro (ex: "cod":"404")
            if (root.has("cod") && !root.path("cod").asText().equals("200")) {
                log.error("🚨 API retornou erro para {}: código {}, mensagem: {}",
                        city, root.path("cod").asText(), root.path("message").asText());
                return null;
            }

            String nomeCidade = root.path("name").asText();
            String pais = root.path("sys").path("country").asText();
            double temp = root.path("main").path("temp").asDouble();
            double umidade = root.path("main").path("humidity").asDouble();
            double precipitacao = 0.0;
            if (root.has("rain") && root.path("rain").has("1h")) {
                precipitacao = root.path("rain").path("1h").asDouble();
            }
            long unixTime = root.path("dt").asLong();
            LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(unixTime), ZoneId.systemDefault());

            log.info("✅ DADOS EXTRAÍDOS: {} - {}°C, {}% umidade", nomeCidade, temp, umidade);
            System.out.println("✅ SUCESSO: " + nomeCidade + " - " + temp + "°C, " + umidade + "% umidade");

            return WeatherReading.builder()
                    .municipio(nomeCidade)
                    .estado(pais)
                    .temperatura(temp)
                    .umidade(umidade)
                    .precipitacao(precipitacao)
                    .timestamp(timestamp)
                    .build();

        } catch (Exception e) {
            log.error("❌ ERRO ao buscar dados para {}: {}", city, e.getMessage(), e);
            return null;
        }

    }
}