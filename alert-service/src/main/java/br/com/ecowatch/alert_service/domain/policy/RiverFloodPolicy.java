package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Política de alerta para enchentes baseada no nível dos rios (ANA / HidroWeb).
 *
 * <p>Monitora cidades ribeirinhas onde o nível do rio pode representar risco
 * de inundação e crise de abastecimento. O alerta é gerado quando o nível
 * atinge ou supera a cota de alerta histórica do SNIRH/ANA.</p>
 *
 * <p>Cotas de alerta monitoradas (referência SNIRH):
 * <ul>
 *   <li>Manaus (Rio Negro): alerta = 28m, emergência = 29m</li>
 *   <li>Porto Alegre (Lago Guaíba): alerta = 2m, emergência = 3m</li>
 *   <li>Recife (Rio Capibaribe): alerta = 3.5m</li>
 *   <li>Fortaleza (Rio Cocó): alerta = 2m</li>
 * </ul>
 * </p>
 */
@Component
public class RiverFloodPolicy implements AlertPolicy {

    // Cotas de alerta por cidade em metros (sincronizado com AnaRiverClient)
    private static final Map<String, Double> COTAS_ALERTA = Map.of(
            "manaus",         28.0,
            "porto alegre",   2.0,
            "recife",         3.5,
            "fortaleza",      2.0,
            "salvador",       1.5
    );

    // Cotas de emergência (nível crítico — risco de vida)
    private static final Map<String, Double> COTAS_EMERGENCIA = Map.of(
            "manaus",         29.0,
            "porto alegre",   3.0,
            "recife",         4.5,
            "fortaleza",      2.8
    );

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.nivelRio() <= 0.0) {
            return AlertResult.noAlert(); // cidade sem monitoramento de rio
        }

        String municipioKey = reading.municipio().toLowerCase().trim();
        Double cotaAlerta = COTAS_ALERTA.get(municipioKey);

        if (cotaAlerta == null || reading.nivelRio() < cotaAlerta) {
            return AlertResult.noAlert();
        }

        Double cotaEmergencia = COTAS_EMERGENCIA.getOrDefault(municipioKey, cotaAlerta + 1.0);
        boolean emergencia = reading.nivelRio() >= cotaEmergencia;

        String emoji  = emergencia ? "🚨" : "⚠️";
        String nivel  = emergencia ? "EMERGÊNCIA" : "ALERTA";
        String acao   = emergencia
                ? "EVACUE imediatamente áreas de risco! Ligue 199 (Defesa Civil)."
                : "Evite margem do rio e áreas de baixada. Monitore alertas da Defesa Civil.";

        String msg = String.format(
                "%s ENCHENTE — COTA DE %s em %s/%s: Nível do rio em %.2fm " +
                "(cota de alerta: %.1fm). %s",
                emoji, nivel, reading.municipio(), reading.estado(),
                reading.nivelRio(), cotaAlerta, acao);

        return new AlertResult(
                true, AlertType.RIVER_FLOOD,
                reading.municipio(), reading.estado(), msg,
                reading.temperatura(), reading.umidade(), reading.precipitacao());
    }
}
