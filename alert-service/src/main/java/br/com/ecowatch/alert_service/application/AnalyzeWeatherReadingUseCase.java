package br.com.ecowatch.alert_service.application;

import br.com.ecowatch.alert_service.domain.model.AlertResult;
import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.domain.policy.AlertPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalyzeWeatherReadingUseCase {

    private final List<AlertPolicy> policies;

    // Construtor: recebe a lista de todas as políticas implementadas
    public AnalyzeWeatherReadingUseCase(List<AlertPolicy> policies) {
        this.policies = policies;
    }

    public List<AlertResult> execute(WeatherReading reading) {
        // Usa a Stream API do Java para passar a leitura por todas as regras
        return policies.stream()
                .map(policy -> policy.evaluate(reading)) // Avalia cada política
                .filter(AlertResult::isAlert)            // Mantém apenas os resultados que geraram alerta
                .collect(Collectors.toList());           // Agrupa os alertas reais em uma lista
    }
}