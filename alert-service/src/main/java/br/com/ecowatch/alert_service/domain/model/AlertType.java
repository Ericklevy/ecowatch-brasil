package br.com.ecowatch.alert_service.domain.model;

public enum AlertType {
    // --- Alertas Climáticos (já existentes) ---
    EXTREME_HEAT("Calor Extremo"),
    EXTREME_RAIN("Chuva Extrema"),
    FIRE_NEAR_URBAN_AREA("Incêndio Próximo a Área Urbana"),
    CRITICAL_FIRE_RISK("Risco Crítico de Incêndio"),
    DROUGHT_RISK("Risco de Seca"),
    DENGUE_OUTBREAK_RISK("Risco de Surto de Dengue"),

    // --- Novos Alertas de APIs Públicas ---
    POOR_AIR_QUALITY("Qualidade do Ar Ruim"),       // Open-Meteo Air Quality
    FOREST_FIRE("Incêndio Florestal Detectado"),    // INPE / BDQueimadas
    FLASH_FLOOD("Risco de Enxurrada"),              // CEMADEN
    RIVER_FLOOD("Enchente / Cota de Alerta do Rio");// ANA

    private final String label;

    AlertType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}