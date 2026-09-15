package br.com.ecowatch.analytics_service.util;

import java.text.Normalizer;

public class CityNameNormalizer {

    private CityNameNormalizer() {}

    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase();
    }
}
