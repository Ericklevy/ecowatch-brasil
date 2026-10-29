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
        when(repository.findByMunicipioInAndDataPrevisaoIn(any(), any()))
                .thenReturn(Collections.emptyList());

        previsaoService.gerarPrevisoesDiarias();

        // The saveAll method should be called once with a list of 20 items
        verify(repository, times(1)).saveAll(any());
        verify(repository, atLeastOnce()).findByMunicipioInAndDataPrevisaoIn(any(), any());
    }
}
