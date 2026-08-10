package br.com.ecowatch.notification_service.domain.model;

public record AlertResult(
        boolean isAlert,
        AlertType type,
        String municipio,
        String estado,
        String message,
        double temperatura,
        double umidade,
        double precipitacao
) {
    public static AlertResult noAlert() {
        return new AlertResult(false, null, null, null, "Condições normais", 0, 0, 0);
    }
}