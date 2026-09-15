package br.com.ecowatch.analytics_service.application.service;

import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoEntity;
import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrevisaoServiceTest {

    @Mock
    private PrevisaoRepository repository;

    @InjectMocks
    private PrevisaoService previsaoService;

    @Test
    void shouldGenerateDailyForecastsWithNormalizedCities() {
        when(repository.findByMunicipioAndDataPrevisaoAfter(anyString(), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        previsaoService.gerarPrevisoesDiarias();

        // 10 cidades base distintas * 2 previsões por cidade = 20 salvamentos esperados
        verify(repository, times(20)).save(any(PrevisaoEntity.class));
        verify(repository, atLeastOnce()).findByMunicipioAndDataPrevisaoAfter(eq("brasilia"), any(LocalDate.class));
        verify(repository, atLeastOnce()).findByMunicipioAndDataPrevisaoAfter(eq("sao paulo"), any(LocalDate.class));
    }
}
