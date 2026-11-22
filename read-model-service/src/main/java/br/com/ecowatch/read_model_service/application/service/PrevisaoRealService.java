package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.PrevisaoDTO;
import br.com.ecowatch.read_model_service.infrastructure.entity.PrevisaoEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.PrevisaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Previsões REAIS para as 27 capitais (D+1 a D+3), a partir da previsão diária da Open-Meteo
 * (grátis, sem chave, uma única chamada para todas as capitais).
 *
 * Os riscos são índices estimados a partir de temperatura máxima, umidade média e chuva:
 * - Incêndio: calor + ar seco + falta de chuva.
 * - Dengue: temperatura ideal do Aedes (~29 °C) + umidade + chuva (água parada).
 * As mesmas fórmulas estão no frontend (ForecastView), para os números baterem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrevisaoRealService {

    record Capital(String municipio, double lat, double lon) {}

    static final List<Capital> CAPITAIS = List.of(
            new Capital("brasilia", -15.7975, -47.8919),
            new Capital("goiania", -16.6869, -49.2648),
            new Capital("cuiaba", -15.6014, -56.0979),
            new Capital("campo grande", -20.4697, -54.6201),
            new Capital("sao paulo", -23.5505, -46.6333),
            new Capital("rio de janeiro", -22.9068, -43.1729),
            new Capital("belo horizonte", -19.9167, -43.9345),
            new Capital("vitoria", -20.3155, -40.3128),
            new Capital("curitiba", -25.4284, -49.2733),
            new Capital("porto alegre", -30.0346, -51.2177),
            new Capital("florianopolis", -27.5954, -48.5480),
            new Capital("salvador", -12.9777, -38.5016),
            new Capital("fortaleza", -3.7327, -38.5270),
            new Capital("recife", -8.0578, -34.8778),
            new Capital("sao luis", -2.5297, -44.2825),
            new Capital("maceio", -9.6658, -35.7353),
            new Capital("teresina", -5.0892, -42.8019),
            new Capital("joao pessoa", -7.1195, -34.8450),
            new Capital("natal", -5.7945, -35.2110),
            new Capital("aracaju", -10.9091, -37.0677),
            new Capital("manaus", -3.1190, -60.0217),
            new Capital("belem", -1.4558, -48.5024),
            new Capital("palmas", -10.1689, -48.3317),
            new Capital("macapa", 0.0349, -51.0694),
            new Capital("boa vista", 2.8195, -60.6714),
            new Capital("rio branco", -9.9754, -67.8249),
            new Capital("porto velho", -8.7612, -63.9004)
    );

    private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
            + "&daily=temperature_2m_max,precipitation_sum,relative_humidity_2m_mean"
            + "&forecast_days=4&timezone=America%%2FSao_Paulo";

    private final PrevisaoRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    static int riscoIncendio(double tMax, double umidade, double chuva) {
        return limitar((tMax - 20) * 3 + (60 - umidade) * 1.2 - chuva * 4);
    }

    static int riscoDengue(double tMax, double umidade, double chuva) {
        double temp = (tMax >= 22 && tMax <= 34) ? 100 - Math.abs(tMax - 29) * 8 : 10;
        double umid = Math.max(0, Math.min(100, (umidade - 50) * 2));
        double agua = Math.max(0, Math.min(100, chuva * 5));
        return limitar(temp * 0.45 + umid * 0.35 + agua * 0.2);
    }

    private static int limitar(double v) {
        return (int) Math.round(Math.max(0, Math.min(100, v)));
    }

    /** Ao subir o serviço, preenche se ainda não houver previsão para amanhã. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        try {
            if (repository.findByDataPrevisaoAfterOrderByDataPrevisaoAsc(LocalDate.now()).isEmpty()) {
                atualizar();
            }
        } catch (Exception e) {
            log.warn("Não consegui preparar as previsões ao iniciar: {}", e.getMessage());
        }
    }

    /** Junto com a coleta de notícias (7h, 13h e 19h). */
    @Scheduled(cron = "0 5 7,13,19 * * *", zone = "America/Sao_Paulo")
    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public int atualizar() {
        String lats = CAPITAIS.stream().map(c -> String.valueOf(c.lat())).collect(Collectors.joining(","));
        String lons = CAPITAIS.stream().map(c -> String.valueOf(c.lon())).collect(Collectors.joining(","));
        try {
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(String.format(URL, lats, lons)))
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Open-Meteo respondeu {} para as previsões", resp.statusCode());
                return 0;
            }
            List<PrevisaoEntity> novas = converter(mapper.readTree(resp.body()));
            if (novas.isEmpty()) return 0;
            repository.deleteByDataPrevisaoAfter(LocalDate.now().minusDays(1));
            repository.saveAll(novas);
            log.info("🌦️ {} previsões reais (Open-Meteo) gravadas para {} capitais.", novas.size(), CAPITAIS.size());
            return novas.size();
        } catch (Exception e) {
            log.warn("Falha ao atualizar previsões reais: {}", e.getMessage());
            return 0;
        }
    }

    List<PrevisaoEntity> converter(JsonNode raiz) {
        List<PrevisaoEntity> lista = new ArrayList<>();
        JsonNode locais = raiz.isArray() ? raiz : mapper.createArrayNode().add(raiz);
        LocalDateTime agora = LocalDateTime.now();
        for (int i = 0; i < locais.size() && i < CAPITAIS.size(); i++) {
            JsonNode d = locais.get(i).path("daily");
            JsonNode dias = d.path("time");
            for (int k = 1; k < dias.size() && k <= 3; k++) {   // índice 0 = hoje
                if (d.path("temperature_2m_max").get(k).isNull()) continue;
                double t = d.path("temperature_2m_max").get(k).asDouble();
                double u = d.path("relative_humidity_2m_mean").get(k).asDouble(60);
                double c = d.path("precipitation_sum").get(k).asDouble(0);
                PrevisaoEntity p = new PrevisaoEntity();
                p.setMunicipio(CAPITAIS.get(i).municipio());
                p.setDataPrevisao(LocalDate.parse(dias.get(k).asText()));
                p.setTemperaturaPrevista(t);
                p.setPrecipitacaoPrevista(c);
                p.setUmidadePrevista(u);
                p.setRiscoIncendio(riscoIncendio(t, u, c));
                p.setRiscoDengue(riscoDengue(t, u, c));
                p.setDataGeracao(agora);
                lista.add(p);
            }
        }
        return lista;
    }

    /** Todas as previsões a partir de amanhã, para a aba Previsões e o mapa (uma chamada só). */
    public List<PrevisaoDTO> todas() {
        return repository.findByDataPrevisaoAfterOrderByDataPrevisaoAsc(LocalDate.now()).stream()
                .map(p -> new PrevisaoDTO(p.getMunicipio(), p.getDataPrevisao(), p.getTemperaturaPrevista(),
                        p.getPrecipitacaoPrevista(), p.getUmidadePrevista(), p.getRiscoIncendio(), p.getRiscoDengue()))
                .toList();
    }
}
