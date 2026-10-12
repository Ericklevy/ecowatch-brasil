package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

/**
 * Política de alerta para incêndio florestal detectado pelo INPE.
 *
 * <p>Gera alerta quando há focos de incêndio ativos na região E as condições
 * climáticas são favoráveis à propagação do fogo (temperatura alta + umidade crítica).
 * A combinação dessas condições indica risco máximo de incêndio florestal.</p>
 *
 * <p>Referência: INPE considera condições críticas quando umidade relativa ≤ 30%
 * e temperatura ≥ 30°C durante estiagem prolongada.</p>
 */
@Component
public class ForestFirePolicy implements AlertPolicy {

    private static final int    FOCOS_MINIMOS    = 1;    // pelo menos 1 foco detectado
    private static final double UMIDADE_CRITICA  = 35.0; // %
    private static final double TEMPERATURA_ALTA = 30.0; // °C

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        boolean temFocos        = reading.focosIncendio() >= FOCOS_MINIMOS;
        boolean umidadeCritica  = reading.umidade() <= UMIDADE_CRITICA;
        boolean temperaturAlta  = reading.temperatura() >= TEMPERATURA_ALTA;

        if (temFocos) {
            String condicao = (umidadeCritica || temperaturAlta)
                    ? "condições EXTREMAS de propagação do fogo"
                    : "risco de propagação de chamas";
            String msg = String.format(
                    "🔥 INCÊNDIO FLORESTAL em %s/%s: %d foco(s) de calor detectado(s) pelo INPE. " +
                    "Temperatura de %.1f°C com %.1f%% de umidade — %s. " +
                    "Mantenha-se afastado de matas e áreas de vegetação. " +
                    "Ligue 193 (Bombeiros) ao avistar fumaça.",
                    reading.municipio(), reading.estado(),
                    reading.focosIncendio(), reading.temperatura(), reading.umidade(), condicao);

            return new AlertResult(
                    true, AlertType.FOREST_FIRE,
                    reading.municipio(), reading.estado(), msg,
                    reading.temperatura(), reading.umidade(), reading.precipitacao());
        }
        return AlertResult.noAlert();
    }
}
