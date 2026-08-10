package br.com.ecowatch.alert_service.domain.policy;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;

public interface AlertPolicy {

    /**
     * Avalia uma leitura climática para determinar se uma anomalia (alerta) ocorreu.
     *
     * @param reading A leitura climática atual chegando do Kafka.
     * @return AlertResult contendo o status do alerta e a mensagem gerada.
     */
    AlertResult evaluate(WeatherReading reading);

}