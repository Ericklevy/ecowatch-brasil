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
            case "Belem", "Belém" -> "-1.4558,-48.5024";
            case "Goiania", "Goiânia" -> "-16.6869,-49.2648";
            case "Sao Luis", "São Luís" -> "-2.5297,-44.2825";
            case "Maceio", "Maceió" -> "-9.6658,-35.7353";
            case "Teresina" -> "-5.0892,-42.8019";
            case "Joao Pessoa", "João Pessoa" -> "-7.1195,-34.8450";
            case "Natal" -> "-5.7945,-35.2110";
            case "Aracaju" -> "-10.9091,-37.0677";
            case "Campo Grande" -> "-20.4697,-54.6201";
            case "Cuiaba", "Cuiabá" -> "-15.6014,-56.0979";
            case "Florianopolis", "Florianópolis" -> "-27.5954,-48.5480";
            case "Vitoria", "Vitória" -> "-20.3155,-40.3128";
            case "Palmas" -> "-10.1689,-48.3317";
            case "Macapa", "Macapá" -> "0.0349,-51.0694";
            case "Boa Vista" -> "2.8195,-60.6714";
            case "Rio Branco" -> "-9.9754,-67.8249";
            case "Porto Velho" -> "-8.7612,-63.9004";
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
            case "Belem", "Belém" -> "PA";
            case "Goiania", "Goiânia" -> "GO";
            case "Sao Luis", "São Luís" -> "MA";
            case "Maceio", "Maceió" -> "AL";
            case "Teresina" -> "PI";
            case "Joao Pessoa", "João Pessoa" -> "PB";
            case "Natal" -> "RN";
            case "Aracaju" -> "SE";
            case "Campo Grande" -> "MS";
            case "Cuiaba", "Cuiabá" -> "MT";
            case "Florianopolis", "Florianópolis" -> "SC";
            case "Vitoria", "Vitória" -> "ES";
            case "Palmas" -> "TO";
            case "Macapa", "Macapá" -> "AP";
            case "Boa Vista" -> "RR";
            case "Rio Branco" -> "AC";
            case "Porto Velho" -> "RO";
            default -> "BR";
        };
    }
}