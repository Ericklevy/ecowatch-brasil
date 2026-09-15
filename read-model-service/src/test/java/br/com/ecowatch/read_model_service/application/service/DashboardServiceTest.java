package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.DashboardDTO;
import br.com.ecowatch.read_model_service.infrastructure.entity.AlertEntity;
import br.com.ecowatch.read_model_service.infrastructure.entity.PrevisaoEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.AlertRepository;
import br.com.ecowatch.read_model_service.infrastructure.persistence.PrevisaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private PrevisaoRepository previsaoRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void shouldReturnDashboardWithNormalizedCityName() {
        AlertEntity alert = new AlertEntity();
        alert.setId(1L);
        alert.setMunicipio("brasilia");
        alert.setEstado("DF");
        alert.setTipoAlerta("EXTREME_RAIN");
        alert.setMensagem("Chuva forte");
        alert.setDataHoraAlerta(LocalDateTime.now());

        PrevisaoEntity previsao = new PrevisaoEntity();
        previsao.setId(1L);
        previsao.setMunicipio("brasilia");
        previsao.setDataPrevisao(LocalDate.now());
        previsao.setTemperaturaPrevista(28.0);
        previsao.setPrecipitacaoPrevista(15.0);
        previsao.setUmidadePrevista(80.0);
        previsao.setRiscoIncendio(10);
        previsao.setRiscoDengue(70);

        when(alertRepository.findByMunicipioIgnoreCaseOrderByDataHoraAlertaDesc("brasilia"))
                .thenReturn(List.of(alert));
        when(previsaoRepository.findByMunicipioIgnoreCaseAndDataPrevisaoAfterOrderByDataPrevisaoAsc(eq("brasilia"), any(LocalDate.class)))
                .thenReturn(List.of(previsao));

        DashboardDTO dashboard = dashboardService.getDashboard("Brasília");

        assertEquals("brasilia", dashboard.getMunicipio());
        assertEquals(1, dashboard.getAlertas().size());
        assertEquals("EXTREME_RAIN", dashboard.getAlertas().get(0).getTipoAlerta());
        assertEquals(1, dashboard.getPrevisoes().size());
        assertEquals(28.0, dashboard.getPrevisoes().get(0).getTemperaturaPrevista());
    }
}
