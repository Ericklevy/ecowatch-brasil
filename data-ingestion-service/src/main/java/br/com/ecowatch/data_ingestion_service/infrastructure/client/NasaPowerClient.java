package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.application.MetricsService;
import br.com.ecowatch.data_ingestion_service.domain.client.WeatherClient;
import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import br.com.ecowatch.data_ingestion_service.util.CityNameNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Primary
public class NasaPowerClient implements WeatherClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    // Coordenadas das cidades (latitude, longitude)
    private static final Map<String, String> COORDENADAS = new HashMap<>();

    static {
        COORDENADAS.put("Brasilia", "-15.7939,-47.8828");
        COORDENADAS.put("Brasília", "-15.7939,-47.8828");
        COORDENADAS.put("Rio de Janeiro", "-22.9028,-43.2075");
        COORDENADAS.put("Sao Paulo", "-23.5475,-46.6361");
        COORDENADAS.put("São Paulo", "-23.5475,-46.6361");
        COORDENADAS.put("Salvador", "-12.9722,-38.5014");
        COORDENADAS.put("Recife", "-8.0476,-34.8770");
        COORDENADAS.put("Porto Alegre", "-30.0346,-51.2177");
        COORDENADAS.put("Curitiba", "-25.4284,-49.2733");
        COORDENADAS.put("Belo Horizonte", "-19.9191,-43.9386");
        COORDENADAS.put("Fortaleza", "-3.7172,-38.5433");
        COORDENADAS.put("Manaus", "-3.1190,-60.0217");
        // 17 novas capitais
        COORDENADAS.put("Belem", "-1.4558,-48.5024");
        COORDENADAS.put("Belém", "-1.4558,-48.5024");
        COORDENADAS.put("Goiania", "-16.6869,-49.2648");
        COORDENADAS.put("Goiânia", "-16.6869,-49.2648");
        COORDENADAS.put("Sao Luis", "-2.5297,-44.2825");
        COORDENADAS.put("São Luís", "-2.5297,-44.2825");
        COORDENADAS.put("Maceio", "-9.6658,-35.7353");
        COORDENADAS.put("Maceió", "-9.6658,-35.7353");
        COORDENADAS.put("Teresina", "-5.0892,-42.8019");
        COORDENADAS.put("Joao Pessoa", "-7.1195,-34.8450");
        COORDENADAS.put("João Pessoa", "-7.1195,-34.8450");
        COORDENADAS.put("Natal", "-5.7945,-35.2110");
        COORDENADAS.put("Aracaju", "-10.9091,-37.0677");
        COORDENADAS.put("Campo Grande", "-20.4697,-54.6201");
        COORDENADAS.put("Cuiaba", "-15.6014,-56.0979");
        COORDENADAS.put("Cuiabá", "-15.6014,-56.0979");
        COORDENADAS.put("Florianopolis", "-27.5954,-48.5480");
        COORDENADAS.put("Florianópolis", "-27.5954,-48.5480");
        COORDENADAS.put("Vitoria", "-20.3155,-40.3128");
        COORDENADAS.put("Vitória", "-20.3155,-40.3128");
        COORDENADAS.put("Palmas", "-10.1689,-48.3317");
        COORDENADAS.put("Macapa", "0.0349,-51.0694");
        COORDENADAS.put("Macapá", "0.0349,-51.0694");
        COORDENADAS.put("Boa Vista", "2.8195,-60.6714");
        COORDENADAS.put("Rio Branco", "-9.9754,-67.8249");
        COORDENADAS.put("Porto Velho", "-8.7612,-63.9004");
    }

    // Comunidade: AG = Agricultura (recomendado para dados climáticos gerais)
    private static final String COMMUNITY = "AG";
    private static final String PARAMETERS = "T2M,PRECTOTCORR,RH2M";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double FILL_VALUE = -999.0;

    public NasaPowerClient(ObjectMapper objectMapper,
                           @Value("${api.nasa.url:https://power.larc.nasa.gov}") String baseUrl,
                           MetricsService metricsService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Override
    public WeatherReading fetchWeather(String city) {
        long start = System.nanoTime();

        String coords = COORDENADAS.get(city.trim());
        if (coords == null) {
            coords = COORDENADAS.get(CityNameNormalizer.normalize(city));
        }
        if (coords == null) {
            log.warn("⚠️ Coordenadas não encontradas para: {}", city);
            return null;
        }

        String[] latLon = coords.split(",");
        String lat = latLon[0];
        String lon = latLon[1];

        // 🔥 CORREÇÃO: busca dados de 5 dias atrás (garante que existam)
        LocalDate dataBusca = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(5);
        String dataStr = dataBusca.format(DATE_FORMATTER);

        String uri = String.format(
                "/api/temporal/daily/point?parameters=%s&community=%s&longitude=%s&latitude=%s&start=%s&end=%s&format=JSON&time-standard=UTC",
                PARAMETERS, COMMUNITY, lon, lat, dataStr, dataStr);

        log.info("🌐 Buscando dados NASA POWER para {} (data: {}) – URL: {}", city, dataBusca, uri);

        try {
            String jsonResponse = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> Mono.error(new RuntimeException("Erro NASA POWER: " + response.statusCode()))
                    )
                    .bodyToMono(String.class)
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)))
                    .block(Duration.ofSeconds(15));

            if (jsonResponse == null || jsonResponse.isEmpty()) {
                log.warn("⚠️ Resposta vazia da NASA POWER para {}", city);
                metricsService.recordNasaError();
                return null;
            }

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode properties = root.path("properties");
            JsonNode parameter = properties.path("parameter");

            // Extrai valores para a data solicitada
            double temperatura = parameter.path("T2M").path(dataStr).asDouble();
            double precipitacao = parameter.path("PRECTOTCORR").path(dataStr).asDouble();
            double umidade = parameter.path("RH2M").path(dataStr).asDouble();

            // 🔥 VALIDAÇÃO: se veio -999.0 (fill_value), registra erro e retorna null (fallback será acionado)
            if (temperatura == FILL_VALUE || umidade == FILL_VALUE || precipitacao == FILL_VALUE) {
                log.warn("⚠️ Dados incompletos (fill_value) para {} na data {}", city, dataBusca);
                metricsService.recordNasaError();
                return null;
            }

            long duration = System.nanoTime() - start;
            metricsService.recordNasaSuccess(TimeUnit.NANOSECONDS.toMillis(duration));

            log.info("✅ Dados NASA POWER obtidos: {} - {}°C, {}% umidade, {}mm precipitação",
                    city, temperatura, umidade, precipitacao);

            return WeatherReading.builder()
                    .municipio(city)
                    .estado(obterEstado(city))
                    .temperatura(temperatura)
                    .umidade(umidade)
                    .precipitacao(precipitacao)
                    .timestamp(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")))
                    .build();

        } catch (Exception e) {
            metricsService.recordNasaError();
            log.error("❌ Erro ao buscar dados NASA POWER para {}: {}", city, e.getMessage());
            return null;
        }
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