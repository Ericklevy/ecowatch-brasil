package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

@Component
public class ExtremeRainPolicy implements AlertPolicy {
    private static final double EXTREME_RAIN_THRESHOLD = 80.0;

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.precipitacao() > EXTREME_RAIN_THRESHOLD) {
            String msg = String.format(
                    "⛈️ Chuva Extrema em %s/%s: Volume de %.1fmm registrado. " +
                    "Risco elevado de enchentes e deslizamentos. Procure abrigo seguro.",
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