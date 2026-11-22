package br.com.ecowatch.read_model_service.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PrevisaoRealServiceTest {

    @Test
    void riscoDeIncendioSobeComCalorESeca() {
        assertTrue(PrevisaoRealService.riscoIncendio(37, 15, 0) >= 90);
        assertEquals(0, PrevisaoRealService.riscoIncendio(24, 90, 20));
    }

    @Test
    void riscoDeDengueAltoNoCalorUmido() {
        assertTrue(PrevisaoRealService.riscoDengue(29, 85, 10) > 70);
        assertTrue(PrevisaoRealService.riscoDengue(37, 15, 0) < 30);
    }

    @Test
    void converteRespostaDaOpenMeteoPulandoHoje() throws Exception {
        String json = """
            [{"daily":{"time":["2026-11-22","2026-11-23","2026-11-24","2026-11-25"],
              "temperature_2m_max":[30,31.5,32,null],
              "precipitation_sum":[0,2.5,0,0],
              "relative_humidity_2m_mean":[70,65,60,55]}}]
            """;
        var lista = new PrevisaoRealService(null).converter(new ObjectMapper().readTree(json));
        assertEquals(2, lista.size());
        assertEquals("brasilia", lista.get(0).getMunicipio());
        assertEquals(LocalDate.of(2026, 11, 23), lista.get(0).getDataPrevisao());
        assertEquals(31.5, lista.get(0).getTemperaturaPrevista());
    }
}
