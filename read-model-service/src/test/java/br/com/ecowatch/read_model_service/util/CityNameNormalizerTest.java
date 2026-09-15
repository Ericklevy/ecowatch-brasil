package br.com.ecowatch.read_model_service.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CityNameNormalizerTest {

    @Test
    void shouldNormalizeCityNames() {
        assertEquals("brasilia", CityNameNormalizer.normalize("Brasília"));
        assertEquals("sao paulo", CityNameNormalizer.normalize("São Paulo"));
        assertEquals("salvador", CityNameNormalizer.normalize("Salvador"));
    }
}
