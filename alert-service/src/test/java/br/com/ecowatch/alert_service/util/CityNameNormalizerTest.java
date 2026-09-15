package br.com.ecowatch.alert_service.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CityNameNormalizerTest {

    @Test
    void shouldNormalizeCityNamesWithAccentsAndUppercase() {
        assertEquals("brasilia", CityNameNormalizer.normalize("Brasília"));
        assertEquals("sao paulo", CityNameNormalizer.normalize("São Paulo"));
        assertEquals("rio de janeiro", CityNameNormalizer.normalize("RIO DE JANEIRO"));
        assertEquals("vitoria", CityNameNormalizer.normalize("Vitória"));
    }

    @Test
    void shouldHandleNullAndEmpty() {
        assertEquals("", CityNameNormalizer.normalize(null));
        assertEquals("", CityNameNormalizer.normalize("   "));
    }
}
