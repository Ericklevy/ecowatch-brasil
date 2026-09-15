package br.com.ecowatch.data_ingestion_service.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CityNameNormalizerTest {

    @Test
    void shouldNormalizeCityNamesInIngestionService() {
        assertEquals("manaus", CityNameNormalizer.normalize("Manaus"));
        assertEquals("fortaleza", CityNameNormalizer.normalize("Fortaleza"));
        assertEquals("sao paulo", CityNameNormalizer.normalize("São Paulo"));
    }
}
