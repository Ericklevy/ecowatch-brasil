package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

@Component
public class HeavyRainPolicy implements AlertPolicy {

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.precipitacao() >= 50.0) {
            String msg = String.format(
                    "🌧️ Chuva Extrema em %s/%s: Precipitação de %.1fmm registrada. " +
                    "Risco de alagamentos e deslizamentos. Evite áreas de encosta e rios.",
                    reading.municipio(), reading.estado(), reading.precipitacao());
            return new AlertResult(
                    true,
                    AlertType.EXTREME_RAIN,
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