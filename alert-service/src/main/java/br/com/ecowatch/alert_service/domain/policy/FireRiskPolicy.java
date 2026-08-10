package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

@Component
public class FireRiskPolicy implements AlertPolicy {
    private static final double MIN_TEMPERATURE = 30.0;
    private static final double MAX_HUMIDITY = 30.0;

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.temperatura() > MIN_TEMPERATURE && reading.umidade() < MAX_HUMIDITY) {
            String msg = String.format(
                    "🔥 Risco Crítico de Incêndio em %s/%s: Temperatura de %.1f°C com umidade de apenas %.1f%%. " +
                    "Condições extremamente favoráveis a queimadas. Não faça fogueiras.",
                    reading.municipio(), reading.estado(), reading.temperatura(), reading.umidade());
            return new AlertResult(
                    true,
                    AlertType.CRITICAL_FIRE_RISK,
                    reading.municipio(),
                    reading.estado(),
                    msg,
                    reading.temperatura(),
                    reading.umidade(),
                    reading.precipitacao()
            );
        }
        return AlertResult.noAlert();
    }
}