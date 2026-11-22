package br.com.ecowatch.read_model_service.util;

import java.text.Normalizer;

public class CityNameNormalizer {

    private CityNameNormalizer() {}

    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String withSpaces = name.trim().replace('-', ' ');
        String normalized = Normalizer.normalize(withSpaces, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
