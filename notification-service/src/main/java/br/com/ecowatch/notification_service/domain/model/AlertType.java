package br.com.ecowatch.notification_service.domain.model;

public enum AlertType {
    EXTREME_HEAT("Calor Extremo"),
    EXTREME_RAIN("Chuva Extrema"),
    FIRE_NEAR_URBAN_AREA("Incêndio Próximo a Área Urbana"),
    CRITICAL_FIRE_RISK("Risco Crítico de Incêndio"),
    DROUGHT_RISK("Risco de Seca"),
    DENGUE_OUTBREAK_RISK("Risco de Surto de Dengue");

    private final String label;

    AlertType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}