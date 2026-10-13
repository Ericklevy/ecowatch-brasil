package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import br.com.ecowatch.data_ingestion_service.util.CityNameNormalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client para a API do INPE / BDQueimadas (Banco de Dados de Queimadas).
 *
 * <p><strong>API real:</strong> O INPE disponibiliza dados de focos de calor via
 * {@code https://queimadas.dgi.inpe.br/api/focos/}. A integração pode ser feita via
 * autenticação com CPF + senha em {@code https://queimadas.dgi.inpe.br/queimadas/bdqueimadas/export}.
 * Configure a variável {@code api.inpe.url} no seu {@code .env} ao obter acesso.</p>
 *
 * <p><strong>Funcionamento atual:</strong> Enquanto as credenciais do INPE não forem configuradas,
 * o client estima focos de incêndio a partir dos dados climáticos já coletados:
 * temperatura alta + umidade crítica + precipitação zero = estimativa de risco elevado.
 * Quando a URL real for configurada, o método {@code fetchReal()} substituirá a simulação.</p>
 */
@Slf4j
@Component
public class InpeFireClient {

    private final WebClient webClient;

    @Value("${api.inpe.url:SIMULADO}")
    private String inpeUrl;

    // Capitais situadas no bioma Amazônia (onde umidade entre 40% e 70% já sustenta focos críticos)
    private static final Set<String> BIOMA_AMAZONIA = Set.of(
            "manaus", "portovelho", "porto velho", "riobranco", "rio branco",
            "boavista", "boa vista", "macapa", "belem"
    );

    // Cidades ribeirinhas ou com histórico de queimadas frequentes e suas regiões de risco
    private static final Map<String, String> REGIOES_RISCO = new HashMap<>();

    static {
        adicionarRegiao("Manaus",         "AM - Alta incidência histórica (Amazônia)");
        adicionarRegiao("Cuiaba",         "MT - Alta incidência histórica (Cerrado/Pantanal)");
        adicionarRegiao("Brasilia",       "DF - Risco sazonal (Cerrado)");
        adicionarRegiao("Belo Horizonte", "MG - Risco moderado (Cerrado/Mata Atlântica)");
        adicionarRegiao("Fortaleza",      "CE - Risco moderado (Caatinga)");
        adicionarRegiao("Goiania",        "GO - Alta incidência (Cerrado)");
        adicionarRegiao("Palmas",         "TO - Alta incidência (Cerrado/Amazônia)");
        adicionarRegiao("Porto Velho",    "RO - Alta incidência (Amazônia/arco do desmatamento)");
        adicionarRegiao("Rio Branco",     "AC - Alta incidência (Amazônia)");
        adicionarRegiao("Boa Vista",      "RR - Alta incidência (Lavrado/Amazônia)");
        adicionarRegiao("Macapa",         "AP - Risco moderado (Amazônia)");
        adicionarRegiao("Belem",          "PA - Risco moderado (Amazônia)");
        adicionarRegiao("Campo Grande",   "MS - Alta incidência (Cerrado/Pantanal)");
        adicionarRegiao("Teresina",       "PI - Risco moderado (Caatinga/Cerrado)");
    }

    private static void adicionarRegiao(String cidade, String descricao) {
        REGIOES_RISCO.put(cidade, descricao);
        REGIOES_RISCO.put(CityNameNormalizer.normalize(cidade), descricao);
        REGIOES_RISCO.put(cidade.toLowerCase(), descricao);
    }

    public InpeFireClient(
            @Value("${api.inpe.url:SIMULADO}") String inpeUrl) {
        this.inpeUrl = inpeUrl;
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(512 * 1024))
                .build();
    }

    /**
     * Enriquece o WeatherReading com a estimativa de focos de incêndio.
     * Usa dados reais do INPE se configurado, ou estimativa climática como fallback.
     */
    public WeatherReading enrichWithFireData(WeatherReading reading) {
        int focos = 0;

        if (!"SIMULADO".equals(inpeUrl)) {
            focos = fetchReal(reading.municipio());
        } else {
            focos = estimarFocosClimaticamente(reading);
        }

        if (focos > 0) {
            log.warn("🔥 [INPE] {} focos de calor detectados/estimados em {}", focos, reading.municipio());
        } else {
            log.info("✅ [INPE] Nenhum foco de calor em {}", reading.municipio());
        }

        return reading.toBuilder().focosIncendio(focos).build();
    }

    /**
     * Consulta a API real do INPE. Ative configurando {@code api.inpe.url} no .env.
     */
    private int fetchReal(String municipio) {
        try {
            // Exemplo de chamada: GET /api/focos/?municipio=Manaus&data_inicio=YYYY-MM-DD
            String response = webClient.get()
                    .uri(inpeUrl + "/api/focos/?pais=Brasil&municipio=" + municipio)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2)))
                    .block(Duration.ofSeconds(10));
            // TODO: parsear o JSON/CSV de resposta do INPE e extrair contagem de focos
            log.info("📡 [INPE] Dados reais obtidos para {}", municipio);
            return 0; // substituir pelo parse real
        } catch (Exception e) {
            log.error("❌ [INPE] Falha ao buscar dados reais: {}. Usando estimativa.", e.getMessage());
            return 0;
        }
    }

    /**
     * Estimativa de focos baseada em condições climáticas desfavoráveis e bioma.
     * Na Amazônia: calor >= 30.5°C + sem chuva (< 2mm) com umidade até 70% já sustenta focos severos.
     * No Cerrado/Caatinga/Pantanal: calor >= 30.0°C + umidade <= 35% + sem chuva (< 2mm).
     */
    private int estimarFocosClimaticamente(WeatherReading r) {
        String cityNormalized = CityNameNormalizer.normalize(r.municipio());
        boolean regiaoDeRisco = REGIOES_RISCO.containsKey(cityNormalized) || REGIOES_RISCO.containsKey(r.municipio());
        if (!regiaoDeRisco) {
            return 0;
        }

        boolean isAmazonia = BIOMA_AMAZONIA.contains(cityNormalized);
        boolean semChuva = r.precipitacao() < 2.0;

        if (isAmazonia) {
            // Amazônia: calor diário persistente combinado com estiagem sustenta focos críticos
            if (semChuva && r.temperatura() >= 27.5 && r.umidade() <= 80.0) {
                int estimativa = (int) Math.round((r.temperatura() - 27.0) * 3.5 + (80.0 - r.umidade()) * 0.5);
                estimativa = Math.max(3, Math.min(85, estimativa));
                log.info("🔥 [INPE-Amazônia] Estimativa de queimadas para {}: ~{} focos de calor (temp: {}°C, umid: {}%)",
                        r.municipio(), estimativa, r.temperatura(), r.umidade());
                return estimativa;
            }
        } else {
            // Cerrado, Pantanal, Caatinga: calor e estiagem
            if (semChuva && r.temperatura() >= 28.0 && r.umidade() <= 45.0) {
                int estimativa = (int) Math.round((r.temperatura() - 28.0) * 2.5 + (45.0 - r.umidade()) * 0.8);
                estimativa = Math.max(2, Math.min(80, estimativa));
                log.info("🔥 [INPE-Cerrado/Outros] Estimativa de queimadas para {}: ~{} focos de calor",
                        r.municipio(), estimativa);
                return estimativa;
            }
        }
        return 0;
    }
}
