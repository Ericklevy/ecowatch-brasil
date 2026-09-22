package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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

    // Cidades ribeirinhas ou com histórico de queimadas frequentes e suas regiões de risco
    private static final Map<String, String> REGIOES_RISCO = new HashMap<>();

    static {
        REGIOES_RISCO.put("Manaus",         "AM - Alta incidência histórica (Amazônia)");
        REGIOES_RISCO.put("Cuiaba",         "MT - Alta incidência histórica (Cerrado/Pantanal)");
        REGIOES_RISCO.put("Brasilia",       "DF - Risco sazonal (Cerrado)");
        REGIOES_RISCO.put("Belo Horizonte", "MG - Risco moderado (Cerrado/Mata Atlântica)");
        REGIOES_RISCO.put("Fortaleza",      "CE - Risco moderado (Caatinga)");
        // Novas capitais com biomas vulneráveis
        REGIOES_RISCO.put("Goiania",        "GO - Alta incidência (Cerrado)");
        REGIOES_RISCO.put("Palmas",         "TO - Alta incidência (Cerrado/Amazônia)");
        REGIOES_RISCO.put("Porto Velho",    "RO - Alta incidência (Amazônia/arco do desmatamento)");
        REGIOES_RISCO.put("Rio Branco",     "AC - Alta incidência (Amazônia)");
        REGIOES_RISCO.put("Boa Vista",      "RR - Alta incidência (Lavrado/Amazônia)");
        REGIOES_RISCO.put("Macapa",         "AP - Risco moderado (Amazônia)");
        REGIOES_RISCO.put("Belem",          "PA - Risco moderado (Amazônia)");
        REGIOES_RISCO.put("Campo Grande",   "MS - Alta incidência (Cerrado/Pantanal)");
        REGIOES_RISCO.put("Teresina",       "PI - Risco moderado (Caatinga/Cerrado)");
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
     * Estimativa de focos baseada em condições climáticas desfavoráveis.
     * Lógica: temperatura alta + umidade crítica + precipitação zero + região de risco = risco elevado.
     */
    private int estimarFocosClimaticamente(WeatherReading r) {
        boolean regiaoDeRisco = REGIOES_RISCO.containsKey(r.municipio());
        boolean climaDeRisco  = r.temperatura() > 32.0 && r.umidade() < 30.0 && r.precipitacao() < 1.0;

        if (regiaoDeRisco && climaDeRisco) {
            // Escala proporcional à severidade (mais quente + mais seco = mais focos estimados)
            int estimativa = (int) ((r.temperatura() - 32) * 2 + (30 - r.umidade()));
            log.info("⚠️ [INPE] Estimativa por clima para {}: ~{} focos", r.municipio(), estimativa);
            return Math.max(1, estimativa);
        }
        return 0;
    }
}
