package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.client.WeatherClient;
import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class OpenMeteoClient implements WeatherClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(ObjectMapper objectMapper,
                           @Value("${api.open-meteo.url:https://api.open-meteo.com/v1}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public WeatherReading fetchWeather(String city) {
        try {
            // Open-Meteo requer latitude/longitude – vamos usar um mapeamento simples
            String latLon = getLatLon(city);
            if (latLon == null) {
                log.warn("⚠️ Coordenadas não encontradas para: {}", city);
                return null;
            }

            // Usa o endpoint 'current' que inclui umidade relativa
            String uri = String.format(
                    "/forecast?latitude=%s&longitude=%s&current=temperature_2m,relative_humidity_2m,precipitation&timezone=America/Sao_Paulo",
                    latLon.split(",")[0], latLon.split(",")[1]);

            log.info("🌐 Buscando dados Open-Meteo para {}", city);

            String jsonResponse = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)))
                    .block(Duration.ofSeconds(10));

            if (jsonResponse == null) {
                log.warn("⚠️ Resposta vazia do Open-Meteo para {}", city);
                return null;
            }

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode current = root.path("current");

            double temperatura = current.path("temperature_2m").asDouble();
            double umidade = current.path("relative_humidity_2m").asDouble();
            double precipitacao = current.path("precipitation").asDouble();

            // O campo 'time' no endpoint 'current' vem como ISO string (ex: "2026-08-09T16:00")
            String timeStr = current.path("time").asText();
            LocalDateTime dataHora;
            try {
                dataHora = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                dataHora = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
            }

            log.info("✅ Dados Open-Meteo obtidos: {} - {}°C, {}% umidade, {}mm precipitação",
                    city, temperatura, umidade, precipitacao);

            return WeatherReading.builder()
                    .municipio(city)
                    .estado(obterEstado(city))
                    .temperatura(temperatura)
                    .umidade(umidade)
                    .precipitacao(precipitacao)
                    .timestamp(dataHora)
                    .build();

        } catch (Exception e) {
            log.error("❌ Erro ao buscar dados Open-Meteo para {}: {}", city, e.getMessage());
            return null;
        }
    }

    private String getLatLon(String city) {
        return switch (city.trim()) {
            case "Brasilia", "Brasília" -> "-15.7939,-47.8828";
            case "Rio de Janeiro" -> "-22.9028,-43.2075";
            case "Sao Paulo", "São Paulo" -> "-23.5475,-46.6361";
            case "Salvador" -> "-12.9722,-38.5014";
            case "Recife" -> "-8.0476,-34.8770";
            case "Porto Alegre" -> "-30.0346,-51.2177";
            case "Curitiba" -> "-25.4284,-49.2733";
            case "Belo Horizonte" -> "-19.9191,-43.9386";
            case "Fortaleza" -> "-3.7172,-38.5433";
            case "Manaus" -> "-3.1190,-60.0217";
            default -> null;
        };
    }

    private String obterEstado(String city) {
        return switch (city.trim()) {
            case "Brasilia", "Brasília" -> "DF";
            case "Rio de Janeiro" -> "RJ";
            case "Sao Paulo", "São Paulo" -> "SP";
            case "Salvador" -> "BA";
            case "Recife" -> "PE";
            case "Porto Alegre" -> "RS";
            case "Curitiba" -> "PR";
            case "Belo Horizonte" -> "MG";
            case "Fortaleza" -> "CE";
            case "Manaus" -> "AM";
            default -> "BR";
        };
    }
}