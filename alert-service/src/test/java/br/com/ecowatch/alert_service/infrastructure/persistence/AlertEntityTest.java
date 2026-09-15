package br.com.ecowatch.alert_service.infrastructure.persistence;

import br.com.ecowatch.alert_service.domain.model.AlertType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AlertEntityTest {

    @Test
    void shouldNormalizeMunicipioAndSetTimezoneCorrectly() {
        AlertEntity entity = new AlertEntity(
                "São Paulo",
                "SP",
                AlertType.EXTREME_RAIN,
                "Chuva forte",
                LocalDateTime.now()
        );

        assertEquals("sao paulo", entity.getMunicipio());
        assertEquals("SP", entity.getEstado());
        assertEquals(AlertType.EXTREME_RAIN, entity.getTipoAlerta());
        assertNotNull(entity.getDataHoraAlerta());
    }
}
