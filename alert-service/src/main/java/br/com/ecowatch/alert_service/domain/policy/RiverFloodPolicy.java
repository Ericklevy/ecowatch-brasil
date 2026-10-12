package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.util.CityNameNormalizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Política de alerta para enchentes baseada no nível dos rios (ANA / HidroWeb).
 *
 * <p>Monitora cidades ribeirinhas onde o nível do rio pode representar risco
 * de inundação e crise de abastecimento. O alerta é gerado quando o nível
 * atinge ou supera a cota de alerta histórica do SNIRH/ANA.</p>
 */
@Component
public class RiverFloodPolicy implements AlertPolicy {

    // Cotas de alerta por cidade em metros (sincronizado com AnaRiverClient)
    private static final Map<String, Double> COTAS_ALERTA = Map.ofEntries(
            Map.entry("manaus",         28.0),
            Map.entry("porto alegre",   2.0),
            Map.entry("recife",         3.5),
            Map.entry("fortaleza",      2.0),
            Map.entry("salvador",       1.5),
            Map.entry("belem",          3.0),
            Map.entry("teresina",       5.5),
            Map.entry("porto velho",    15.0),
            Map.entry("rio branco",     14.0),
            Map.entry("macapa",         2.5),
            Map.entry("cuiaba",         8.5),
            Map.entry("campo grande",   2.0),
            Map.entry("boa vista",      6.0),
            Map.entry("sao luis",       2.0)
    );

    // Cotas de emergência (nível crítico — risco de vida)
    private static final Map<String, Double> COTAS_EMERGENCIA = Map.ofEntries(
            Map.entry("manaus",         29.0),
            Map.entry("porto alegre",   3.0),
            Map.entry("recife",         4.5),
            Map.entry("fortaleza",      2.8),
            Map.entry("salvador",       2.5),
            Map.entry("belem",          4.0),
            Map.entry("teresina",       6.5),
            Map.entry("porto velho",    17.0),
            Map.entry("rio branco",     17.0),
            Map.entry("macapa",         3.5),
            Map.entry("cuiaba",         10.0),
            Map.entry("campo grande",   3.0),
            Map.entry("boa vista",      8.0),
            Map.entry("sao luis",       3.0)
    );

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.nivelRio() <= 0.0) {
            return AlertResult.noAlert(); // cidade sem monitoramento de rio
        }

        String municipioKey = CityNameNormalizer.normalize(reading.municipio());
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
