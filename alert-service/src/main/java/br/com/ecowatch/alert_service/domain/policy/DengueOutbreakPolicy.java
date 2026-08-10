package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

@Component
public class DengueOutbreakPolicy implements AlertPolicy {
    private static final double MIN_TEMPERATURE = 25.0;
    private static final double MIN_HUMIDITY = 80.0;

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        if (reading.temperatura() > MIN_TEMPERATURE && reading.umidade() > MIN_HUMIDITY) {
            String msg = String.format(
                    "🦟 Risco de Surto de Dengue em %s/%s: Temperatura de %.1f°C com umidade de %.1f%%. " +
                    "Condições favoráveis à proliferação do mosquito Aedes aegypti. Elimine água parada.",
                    reading.municipio(), reading.estado(), reading.temperatura(), reading.umidade());
            return new AlertResult(
                    true,
                    AlertType.DENGUE_OUTBREAK_RISK,
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