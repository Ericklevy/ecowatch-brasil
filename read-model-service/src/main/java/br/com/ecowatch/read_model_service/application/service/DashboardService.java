package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.AlertDTO;
import br.com.ecowatch.read_model_service.application.dto.DashboardDTO;
import br.com.ecowatch.read_model_service.application.dto.PrevisaoDTO;
import br.com.ecowatch.read_model_service.infrastructure.persistence.AlertRepository;
import br.com.ecowatch.read_model_service.infrastructure.persistence.PrevisaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AlertRepository alertRepository;
    private final PrevisaoRepository previsaoRepository;

    @Cacheable(value = "dashboard", key = "#municipio")
    public DashboardDTO getDashboard(String municipio) {
        log.info("📊 Buscando dashboard para {}", municipio);

        // Últimos 10 alertas do município
        var alertas = alertRepository.findByMunicipioOrderByDataHoraAlertaDesc(municipio)
                .stream()
                .limit(10)
                .map(alert -> new AlertDTO(
                        alert.getId(),
                        alert.getMunicipio(),
                        alert.getEstado(),
                        alert.getTipoAlerta(),
                        alert.getMensagem(),
                        alert.getDataHoraAlerta()
                ))
                .collect(Collectors.toList());

        // Previsões para os próximos 3 dias
        var previsoes = previsaoRepository.findByDataPrevisaoAfterOrderByDataPrevisaoAsc(LocalDate.now().minusDays(1))
                .stream()
                .limit(3)
                .filter(p -> p.getMunicipio().equalsIgnoreCase(municipio))
                .map(p -> new PrevisaoDTO(
                        p.getMunicipio(),
                        p.getDataPrevisao(),
                        p.getTemperaturaPrevista(),
                        p.getPrecipitacaoPrevista(),
                        p.getUmidadePrevista(),
                        p.getRiscoIncendio(),
                        p.getRiscoDengue()
                ))
                .collect(Collectors.toList());

        return new DashboardDTO(municipio, alertas, previsoes);
    }
}