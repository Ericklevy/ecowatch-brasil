package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import org.springframework.stereotype.Component;

/**
 * Política de alerta para qualidade do ar ruim.
 *
 * <p>Avalia PM2.5 e PM10 da leitura (coletados via Open-Meteo Air Quality API).
 * Usa os limites diários da OMS (2021):
 * <ul>
 *   <li>PM2.5: 15 µg/m³ (OMS) — alertamos com > 35 µg/m³ (nível "Ruim" CONAMA)</li>
 *   <li>PM10: 45 µg/m³ (OMS) — alertamos com > 75 µg/m³ (nível "Ruim" CONAMA)</li>
 * </ul>
 * </p>
 *
 * <p>Durante secas, a qualidade do ar piora: poeira e fumaça de queimadas elevam PM2.5
 * e PM10 significativamente, especialmente no Centro-Oeste e Norte do Brasil.</p>
 */
@Component
public class AirQualityPolicy implements AlertPolicy {

    // Limiares baseados no índice de qualidade do ar da CONAMA (nível "Ruim")
    private static final double PM25_LIMITE = 35.0;  // µg/m³
    private static final double PM10_LIMITE = 75.0;  // µg/m³

    @Override
    public AlertResult evaluate(WeatherReading reading) {
        boolean pm25Critico = reading.pm25() > PM25_LIMITE;
        boolean pm10Critico = reading.pm10() > PM10_LIMITE;

        if (pm25Critico || pm10Critico) {
            String detalhe = montarDetalhe(reading, pm25Critico, pm10Critico);
            String msg = String.format(
                    "😷 Qualidade do Ar RUIM em %s/%s: %s. " +
                    "Grupos de risco (idosos, crianças, cardiopatas) devem evitar atividades ao ar livre. " +
                    "Use máscara em ambientes externos.",
                    reading.municipio(), reading.estado(), detalhe);

            return new AlertResult(
                    true, AlertType.POOR_AIR_QUALITY,
                    reading.municipio(), reading.estado(), msg,
                    reading.temperatura(), reading.umidade(), reading.precipitacao());
        }
        return AlertResult.noAlert();
    }

    private String montarDetalhe(WeatherReading r, boolean pm25Critico, boolean pm10Critico) {
        if (pm25Critico && pm10Critico) {
            return String.format("PM2.5=%.1f µg/m³ e PM10=%.1f µg/m³ acima dos limites seguros", r.pm25(), r.pm10());
        } else if (pm25Critico) {
            return String.format("PM2.5=%.1f µg/m³ (limite: %.0f µg/m³)", r.pm25(), PM25_LIMITE);
        } else {
            return String.format("PM10=%.1f µg/m³ (limite: %.0f µg/m³)", r.pm10(), PM10_LIMITE);
        }
    }
}
