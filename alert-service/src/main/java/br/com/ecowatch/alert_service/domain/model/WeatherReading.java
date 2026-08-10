package br.com.ecowatch.alert_service.domain.model;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record WeatherReading(
        String municipio,
        String estado,
        double temperatura,
        double umidade,
        double precipitacao,
        LocalDateTime timestamp
) {}