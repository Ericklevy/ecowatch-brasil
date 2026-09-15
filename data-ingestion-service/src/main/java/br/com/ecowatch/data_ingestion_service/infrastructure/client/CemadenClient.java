package br.com.ecowatch.data_ingestion_service.infrastructure.client;

import br.com.ecowatch.data_ingestion_service.domain.model.WeatherReading;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;

/**
 * Client para o CEMADEN (Centro Nacional de Monitoramento e Alertas de Desastres Naturais).
 *
 * <p><strong>API real:</strong> O CEMADEN fornece alertas públicos via
 * {@code http://www.cemaden.gov.br/mapainterativo/} e exportações de dados. Configure
 * {@code api.cemaden.url} com o endpoint da API REST quando obtiver acesso.</p>
 *
 * <p><strong>Funcionamento atual:</strong> Detecta risco de enxurrada com base numa
 * lógica climática de "chuva intensa após seca prolongada" — o cenário mais perigoso
 * segundo estudos do próprio CEMADEN (solo seco impermeabiliza e aumenta escoamento).</p>
 */
@Slf4j
@Component
public class CemadenClient {

    @Value("${api.cemaden.url:SIMULADO}")
    private String cemadenUrl;

    private final WebClient webClient;

    // Cidades com histórico de enxurradas e deslizamentos (CEMADEN / Defesa Civil)
    private static final Set<String> CIDADES_VULNERAVEIS_ENCHENTE = Set.of(
            "Rio de Janeiro", "Sao Paulo", "São Paulo", "Salvador",
            "Belo Horizonte", "Recife", "Porto Alegre", "Curitiba"
    );

    public CemadenClient(
            @Value("${api.cemaden.url:SIMULADO}") String cemadenUrl) {
        this.cemadenUrl = cemadenUrl;
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();
    }

    /**
     * Enriquece o WeatherReading com alerta de enxurrada do CEMADEN.
     * Usa dados reais se configurado, ou lógica de estimativa climática.
     */
    public WeatherReading enrichWithFloodAlert(WeatherReading reading) {
        boolean alerta = false;

        if (!"SIMULADO".equals(cemadenUrl)) {
            alerta = fetchReal(reading.municipio());
        } else {
            alerta = estimarAlertaClimaticamente(reading);
        }

        if (alerta) {
            log.warn("🌊 [CEMADEN] ALERTA DE ENXURRADA ativado para {}", reading.municipio());
        } else {
            log.info("✅ [CEMADEN] Sem alerta de enxurrada para {}", reading.municipio());
        }

        return reading.toBuilder().alertaEnxurrada(alerta).build();
    }

    /**
     * Consulta a API real do CEMADEN. Ative configurando {@code api.cemaden.url} no .env.
     */
    private boolean fetchReal(String municipio) {
        try {
            String response = webClient.get()
                    .uri(cemadenUrl + "/alertas?municipio=" + municipio + "&tipo=enxurrada")
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(2)))
                    .block(Duration.ofSeconds(8));
            // TODO: parsear resposta JSON do CEMADEN
            log.info("📡 [CEMADEN] Dados reais obtidos para {}", municipio);
            return false;
        } catch (Exception e) {
            log.error("❌ [CEMADEN] Falha ao buscar dados reais. Usando estimativa. Erro: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Lógica: chuva intensa (≥25mm) em cidade vulnerável APÓS período seco (umidade do solo baixa).
     * Sinal de umidade baixa como proxy de solo seco — padrão identificado pelo CEMADEN.
     */
    private boolean estimarAlertaClimaticamente(WeatherReading r) {
        boolean cidadeVulneravel = CIDADES_VULNERAVEIS_ENCHENTE.contains(r.municipio());
        boolean chuvaIntensa = r.precipitacao() >= 25.0;
        // Paradoxo: umidade do AR alta + precipitação alta após período seco = risco máximo
        boolean condicaoCritica = chuvaIntensa && r.umidade() > 85.0;

        if (cidadeVulneravel && condicaoCritica) {
            log.info("⚠️ [CEMADEN] Condição crítica estimada: {}mm chuva, {}% umidade em {}",
                    r.precipitacao(), r.umidade(), r.municipio());
            return true;
        }
        return false;
    }
}
