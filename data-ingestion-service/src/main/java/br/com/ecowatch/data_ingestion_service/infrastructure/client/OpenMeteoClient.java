package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.client.WeatherClient;
import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import br.com.ecowatch.data_ingestion_service.util.CityNameNormalizer;
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
        String mun = CityNameNormalizer.normalize(city);
        return switch (mun) {
            case "brasilia" -> "-15.7939,-47.8828";
            case "rio de janeiro" -> "-22.9028,-43.2075";
            case "sao paulo" -> "-23.5475,-46.6361";
            case "salvador" -> "-12.9722,-38.5014";
            case "recife" -> "-8.0476,-34.8770";
            case "porto alegre" -> "-30.0346,-51.2177";
            case "curitiba" -> "-25.4284,-49.2733";
            case "belo horizonte" -> "-19.9191,-43.9386";
            case "fortaleza" -> "-3.7172,-38.5433";
            case "manaus" -> "-3.1190,-60.0217";
            case "belem" -> "-1.4558,-48.5024";
            case "goiania" -> "-16.6869,-49.2648";
            case "sao luis" -> "-2.5297,-44.2825";
            case "maceio" -> "-9.6658,-35.7353";
            case "teresina" -> "-5.0892,-42.8019";
            case "joao pessoa" -> "-7.1195,-34.8450";
            case "natal" -> "-5.7945,-35.2110";
            case "aracaju" -> "-10.9091,-37.0677";
            case "campo grande" -> "-20.4697,-54.6201";
            case "cuiaba" -> "-15.6014,-56.0979";
            case "florianopolis" -> "-27.5954,-48.5480";
            case "vitoria" -> "-20.3155,-40.3128";
            case "palmas" -> "-10.1689,-48.3317";
            case "macapa" -> "0.0349,-51.0694";
            case "boa vista" -> "2.8195,-60.6714";
            case "rio branco" -> "-9.9754,-67.8249";
            case "porto velho" -> "-8.7612,-63.9004";
            default -> null;
        };
    }

    private String obterEstado(String city) {
        String mun = CityNameNormalizer.normalize(city);
        return switch (mun) {
            case "brasilia" -> "DF";
            case "rio de janeiro" -> "RJ";
            case "sao paulo" -> "SP";
            case "salvador" -> "BA";
            case "recife" -> "PE";
            case "porto alegre" -> "RS";
            case "curitiba" -> "PR";
            case "belo horizonte" -> "MG";
            case "fortaleza" -> "CE";
            case "manaus" -> "AM";
            case "belem" -> "PA";
            case "goiania" -> "GO";
            case "sao luis" -> "MA";
            case "maceio" -> "AL";
            case "teresina" -> "PI";
            case "joao pessoa" -> "PB";
            case "natal" -> "RN";
            case "aracaju" -> "SE";
            case "campo grande" -> "MS";
            case "cuiaba" -> "MT";
            case "florianopolis" -> "SC";
            case "vitoria" -> "ES";
            case "palmas" -> "TO";
            case "macapa" -> "AP";
            case "boa vista" -> "RR";
            case "rio branco" -> "AC";
            case "porto velho" -> "RO";
            default -> "BR";
        };
    }
}