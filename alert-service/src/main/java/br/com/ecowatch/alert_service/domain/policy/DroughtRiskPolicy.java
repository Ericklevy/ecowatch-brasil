package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

@Component
public class DroughtRiskPolicy implements AlertPolicy {

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.precipitacao() < 5.0 && reading.umidade() <= 30.0) {
            String msg = String.format(
                    "🚨 Alerta de Seca em %s/%s: Umidade em %.1f%% (crítica) e precipitação de apenas %.1fmm. " +
                    "Evite queimadas e mantenha-se hidratado.",
                    reading.municipio(), reading.estado(), reading.umidade(), reading.precipitacao());
            return new AlertResult(
                    true,
                    AlertType.DROUGHT_RISK,
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