package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Client para a API de Qualidade do Ar da Open-Meteo.
 *
 * <p>API gratuita, sem necessidade de chave. Retorna PM2.5 e PM10 atuais
 * baseados em latitude/longitude. Documentação: https://open-meteo.com/en/docs/air-quality-api</p>
 *
 * <p>Thresholds baseados no índice da OMS:
 * <ul>
 *   <li>PM2.5 > 25 µg/m³ = Moderado; > 50 = Ruim; > 75 = Muito Ruim</li>
 *   <li>PM10 > 50 µg/m³ = Moderado; > 100 = Ruim</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class OpenMeteoAirQualityClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Mapa de cidades → coordenadas (deve ser mantido em sincronia com os outros clients)
    private static final Map<String, double[]> COORDENADAS = new HashMap<>();

    static {
        COORDENADAS.put("Brasilia",       new double[]{-15.7939, -47.8828});
        COORDENADAS.put("Rio de Janeiro", new double[]{-22.9028, -43.2075});
        COORDENADAS.put("Sao Paulo",      new double[]{-23.5475, -46.6361});
        COORDENADAS.put("Salvador",       new double[]{-12.9722, -38.5014});
        COORDENADAS.put("Recife",         new double[]{-8.0476,  -34.8770});
        COORDENADAS.put("Porto Alegre",   new double[]{-30.0346, -51.2177});
        COORDENADAS.put("Curitiba",       new double[]{-25.4284, -49.2733});
        COORDENADAS.put("Belo Horizonte", new double[]{-19.9191, -43.9386});
        COORDENADAS.put("Fortaleza",      new double[]{-3.7172,  -38.5433});
        COORDENADAS.put("Manaus",         new double[]{-3.1190,  -60.0217});
        // 17 novas capitais
        COORDENADAS.put("Belem",          new double[]{-1.4558,  -48.5024});
        COORDENADAS.put("Goiania",        new double[]{-16.6869, -49.2648});
        COORDENADAS.put("Sao Luis",       new double[]{-2.5297,  -44.2825});
        COORDENADAS.put("Maceio",         new double[]{-9.6658,  -35.7353});
        COORDENADAS.put("Teresina",       new double[]{-5.0892,  -42.8019});
        COORDENADAS.put("Joao Pessoa",    new double[]{-7.1195,  -34.8450});
        COORDENADAS.put("Natal",          new double[]{-5.7945,  -35.2110});
        COORDENADAS.put("Aracaju",        new double[]{-10.9091, -37.0677});
        COORDENADAS.put("Campo Grande",   new double[]{-20.4697, -54.6201});
        COORDENADAS.put("Cuiaba",         new double[]{-15.6014, -56.0979});
        COORDENADAS.put("Florianopolis",  new double[]{-27.5954, -48.5480});
        COORDENADAS.put("Vitoria",        new double[]{-20.3155, -40.3128});
        COORDENADAS.put("Palmas",         new double[]{-10.1689, -48.3317});
        COORDENADAS.put("Macapa",         new double[]{0.0349,   -51.0694});
        COORDENADAS.put("Boa Vista",      new double[]{2.8195,   -60.6714});
        COORDENADAS.put("Rio Branco",     new double[]{-9.9754,  -67.8249});
        COORDENADAS.put("Porto Velho",    new double[]{-8.7612,  -63.9004});
    }

    public OpenMeteoAirQualityClient(
            ObjectMapper objectMapper,
            @Value("${api.open-meteo.air-quality-url:https://air-quality-api.open-meteo.com/v1}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(512 * 1024))
                .build();
    }

    /**
     * Enriquece um WeatherReading existente com dados de qualidade do ar (PM2.5 e PM10).
     *
     * @param reading Leitura base já coletada do clima.
     * @return WeatherReading enriquecido, ou o original em caso de falha.
     */
    public WeatherReading enrichWithAirQuality(WeatherReading reading) {
        String city = reading.municipio();
        double[] coords = COORDENADAS.get(city.trim());

        if (coords == null) {
            log.warn("⚠️ [AirQuality] Coordenadas não encontradas para: {}", city);
            return reading;
        }

        try {
            String uri = String.format(
                    "/air-quality?latitude=%s&longitude=%s&current=pm2_5,pm10&timezone=America/Sao_Paulo",
                    coords[0], coords[1]);

            log.info("🌫️ Buscando qualidade do ar (Open-Meteo) para {}", city);

            String json = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)))
                    .block(Duration.ofSeconds(10));

            if (json == null) {
                log.warn("⚠️ [AirQuality] Resposta vazia para {}", city);
                return reading;
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode current = root.path("current");

            double pm25 = current.path("pm2_5").asDouble(0.0);
            double pm10 = current.path("pm10").asDouble(0.0);

            log.info("✅ [AirQuality] {}: PM2.5={} µg/m³ | PM10={} µg/m³", city, pm25, pm10);

            return reading.toBuilder()
                    .pm25(pm25)
                    .pm10(pm10)
                    .build();

        } catch (Exception e) {
            log.error("❌ [AirQuality] Erro ao buscar qualidade do ar para {}: {}", city, e.getMessage());
            return reading; // retorna o original sem falhar o ciclo inteiro
        }
    }
}
