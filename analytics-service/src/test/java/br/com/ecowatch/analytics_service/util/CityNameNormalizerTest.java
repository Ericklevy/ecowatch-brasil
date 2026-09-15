package br.com.ecowatch.analytics_service.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CityNameNormalizerTest {

    @Test
    void shouldNormalizeCityNamesInAnalyticsService() {
        assertEquals("porto alegre", CityNameNormalizer.normalize("Porto Alegre"));
        assertEquals("curitiba", CityNameNormalizer.normalize("Curitiba"));
        assertEquals("belem", CityNameNormalizer.normalize("Belém"));
    }
}
