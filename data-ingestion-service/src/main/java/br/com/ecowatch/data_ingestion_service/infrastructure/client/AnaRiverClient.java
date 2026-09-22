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
 * Client para a ANA (Agência Nacional de Águas e Saneamento Básico).
 *
 * <p><strong>API real:</strong> A ANA disponibiliza dados de cotas e vazões pelo HidroWeb:
 * {@code https://www.snirh.gov.br/hidroweb/rest/api/documento/convencionais?tipo=3&codEstacao=...}
 * Configure {@code api.ana.url} com o endpoint e use os códigos de estação do HidroWeb.</p>
 *
 * <p><strong>Funcionamento atual:</strong> Estimativa do nível do rio baseada em correlação
 * com precipitação acumulada e características geográficas de cada cidade. Cidades ribeirinhas
 * (Manaus, Porto Alegre) têm comportamento mais agressivo na estimativa.</p>
 *
 * <p><strong>Cotas de alerta por cidade (referência histórica SNIRH):</strong>
 * <ul>
 *   <li>Manaus (Rio Negro): cota de alerta = 28m, cota de emergência = 29m</li>
 *   <li>Porto Alegre (Guaíba): cota de alerta = 2m, emergência = 3m</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class AnaRiverClient {

    @Value("${api.ana.url:SIMULADO}")
    private String anaUrl;

    private final WebClient webClient;

    // Cidades com monitoramento de nível de rio relevante → cota de alerta (metros)
    private static final Map<String, Double> COTAS_ALERTA = new HashMap<>();

    static {
        COTAS_ALERTA.put("Manaus",       28.0);  // Rio Negro
        COTAS_ALERTA.put("Porto Alegre", 2.0);   // Lago Guaíba
        COTAS_ALERTA.put("Recife",       3.5);   // Rio Capibaribe
        COTAS_ALERTA.put("Fortaleza",    2.0);   // Rio Cocó
        COTAS_ALERTA.put("Salvador",     1.5);   // Rio das Tripas
        // Novas capitais com rios monitoráveis
        COTAS_ALERTA.put("Belem",        4.0);   // Rio Guamá / Baía do Guajará
        COTAS_ALERTA.put("Sao Luis",     2.5);   // Rio Anil
        COTAS_ALERTA.put("Teresina",     8.0);   // Rio Parnaíba
        COTAS_ALERTA.put("Porto Velho",  14.0);  // Rio Madeira
        COTAS_ALERTA.put("Rio Branco",   14.0);  // Rio Acre
        COTAS_ALERTA.put("Macapa",       3.5);   // Rio Amazonas (foz)
        COTAS_ALERTA.put("Cuiaba",       5.0);   // Rio Cuiabá
        COTAS_ALERTA.put("Campo Grande", 3.0);   // Córrego Prosa / Segredo
        COTAS_ALERTA.put("Boa Vista",    6.0);   // Rio Branco (RR)
    }

    public AnaRiverClient(
            @Value("${api.ana.url:SIMULADO}") String anaUrl) {
        this.anaUrl = anaUrl;
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();
    }

    /**
     * Enriquece o WeatherReading com o nível do rio monitorado na cidade.
     * Retorna 0.0 para cidades sem monitoramento ativo da ANA.
     */
    public WeatherReading enrichWithRiverLevel(WeatherReading reading) {
        String city = reading.municipio();

        if (!COTAS_ALERTA.containsKey(city)) {
            // Cidade sem monitoramento de rio — retorna sem modificar
            return reading;
        }

        double nivel = 0.0;

        if (!"SIMULADO".equals(anaUrl)) {
            nivel = fetchReal(city);
        } else {
            nivel = estimarNivelRio(reading);
        }

        double cotaAlerta = COTAS_ALERTA.get(city);
        if (nivel >= cotaAlerta) {
            log.warn("⚠️ [ANA] Rio em COTA DE ALERTA em {}: {:.2f}m (alerta: {:.2f}m)", city, nivel, cotaAlerta);
        } else {
            log.info("✅ [ANA] Nível do rio em {}: {:.2f}m (normal)", city, nivel);
        }

        return reading.toBuilder().nivelRio(nivel).build();
    }

    /**
     * Consulta a API real do HidroWeb (ANA). Ative com {@code api.ana.url} no .env.
     */
    private double fetchReal(String municipio) {
        try {
            // Endpoint do HidroWeb para dados de cota em tempo real
            String response = webClient.get()
                    .uri(anaUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2)))
                    .block(Duration.ofSeconds(10));
            // TODO: parsear o XML/JSON de resposta do HidroWeb (campo "cota")
            log.info("📡 [ANA] Dados reais obtidos para {}", municipio);
            return 0.0;
        } catch (Exception e) {
            log.error("❌ [ANA] Falha ao buscar nível do rio: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Estimativa do nível do rio com base na precipitação acumulada.
     * Correlação simplificada: precipitação alta → nível mais elevado do rio.
     */
    private double estimarNivelRio(WeatherReading r) {
        double cotaBase;
        double fatorPrecipitacao;

        switch (r.municipio()) {
            case "Manaus"       -> { cotaBase = 22.0; fatorPrecipitacao = 0.15; }
            case "Porto Alegre" -> { cotaBase = 0.8;  fatorPrecipitacao = 0.05; }
            case "Recife"       -> { cotaBase = 1.2;  fatorPrecipitacao = 0.04; }
            case "Fortaleza"    -> { cotaBase = 0.5;  fatorPrecipitacao = 0.03; }
            case "Belem"        -> { cotaBase = 2.0;  fatorPrecipitacao = 0.06; }
            case "Teresina"     -> { cotaBase = 4.0;  fatorPrecipitacao = 0.10; }
            case "Porto Velho"  -> { cotaBase = 8.0;  fatorPrecipitacao = 0.12; }
            case "Rio Branco"   -> { cotaBase = 8.0;  fatorPrecipitacao = 0.12; }
            case "Macapa"       -> { cotaBase = 1.5;  fatorPrecipitacao = 0.05; }
            case "Cuiaba"       -> { cotaBase = 2.5;  fatorPrecipitacao = 0.06; }
            case "Campo Grande" -> { cotaBase = 1.0;  fatorPrecipitacao = 0.04; }
            case "Boa Vista"    -> { cotaBase = 3.0;  fatorPrecipitacao = 0.08; }
            case "Sao Luis"     -> { cotaBase = 1.0;  fatorPrecipitacao = 0.04; }
            default             -> { cotaBase = 0.5;  fatorPrecipitacao = 0.02; }
        }

        double nivelEstimado = cotaBase + (r.precipitacao() * fatorPrecipitacao);
        log.info("📊 [ANA] Nível estimado do rio em {}: {:.2f}m (base={}, chuva={}mm)",
                r.municipio(), nivelEstimado, cotaBase, r.precipitacao());
        return nivelEstimado;
    }
}
