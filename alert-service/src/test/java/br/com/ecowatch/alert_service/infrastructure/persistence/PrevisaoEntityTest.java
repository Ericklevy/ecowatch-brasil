package br.com.ecowatch.alert_service.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrevisaoEntityTest {

    @Test
    void shouldNormalizeMunicipioAndSetDataGeracao() {
        PrevisaoEntity entity = new PrevisaoEntity(
                "Florianópolis",
                LocalDate.now(),
                26.5,
                10.0,
                70.0,
                20,
                60
        );

        assertEquals("florianopolis", entity.getMunicipio());
        assertEquals(26.5, entity.getTemperaturaPrevista());
        assertNotNull(entity.getDataGeracao());
    }
}
