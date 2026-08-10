package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

@Component
public class ExtremeHeatPolicy implements AlertPolicy {

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.temperatura() >= 35.0) {
            String msg = String.format(
                    "🌡️ Calor Extremo em %s/%s: Temperatura de %.1f°C registrada. " +
                    "Risco de insolação e desidratação. Evite exposição ao sol entre 10h e 16h.",
                    reading.municipio(), reading.estado(), reading.temperatura());
            return new AlertResult(
                    true,
                    AlertType.EXTREME_HEAT,
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