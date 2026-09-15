package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

/**
 * Política de alerta para risco de enxurrada (CEMADEN).
 *
 * <p>Gera alerta quando o CEMADEN sinaliza risco de enxurrada ({@code alertaEnxurrada=true})
 * com chuva intensa. O cenário mais perigoso segundo o CEMADEN é a chuva forte
 * após um período de seca, quando o solo está impermeabilizado e todo o volume
 * de chuva vira escoamento superficial direto para rios e ruas.</p>
 *
 * <p>Este alerta é diferente do {@code HeavyRainPolicy} pois considera a situação
 * de risco real reportada pelo sistema governamental de monitoramento, não apenas
 * o volume de precipitação isolado.</p>
 */
@Component
public class CemadenFloodPolicy implements AlertPolicy {

    // Limiar mínimo de chuva para considerar o alerta do CEMADEN
    private static final double CHUVA_MINIMA_MM = 15.0;

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        boolean cemadenAlertou = reading.alertaEnxurrada();
        boolean chuvaIntensa   = reading.precipitacao() >= CHUVA_MINIMA_MM;

        if (cemadenAlertou && chuvaIntensa) {
            String msg = String.format(
                    "🌊 ALERTA DE ENXURRADA (CEMADEN) em %s/%s: Chuva de %.1fmm com solo saturado. " +
                    "Risco de enxurradas e deslizamentos em áreas de encosta e baixadas. " +
                    "Evite áreas de risco. Em caso de emergência, ligue 199 (Defesa Civil) ou 193 (Bombeiros).",
                    reading.municipio(), reading.estado(), reading.precipitacao());

            return new AlertResult(
                    true, AlertType.FLASH_FLOOD,
                    reading.municipio(), reading.estado(), msg,
                    reading.temperatura(), reading.umidade(), reading.precipitacao());
        }
        return AlertResult.noAlert();
    }
}
