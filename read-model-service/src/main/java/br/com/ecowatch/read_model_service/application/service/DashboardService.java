package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.AlertDTO;
import br.com.ecowatch.read_model_service.application.dto.DashboardDTO;
import br.com.ecowatch.read_model_service.application.dto.PrevisaoDTO;
import br.com.ecowatch.read_model_service.infrastructure.persistence.AlertRepository;
import br.com.ecowatch.read_model_service.infrastructure.persistence.PrevisaoRepository;
import br.com.ecowatch.read_model_service.util.CityNameNormalizer;
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

        String normalizedQuery = CityNameNormalizer.normalize(municipio);

        // Busca alertas por município padronizado
        var alertEntities = alertRepository.findByMunicipioIgnoreCaseOrderByDataHoraAlertaDesc(normalizedQuery);
        if (alertEntities.isEmpty()) {
            alertEntities = alertRepository.findAllByOrderByDataHoraAlertaDesc()
                    .stream()
                    .filter(a -> CityNameNormalizer.normalize(a.getMunicipio()).equals(normalizedQuery))
                    .collect(Collectors.toList());
        }

        var alertas = alertEntities.stream()
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
        var previsaoEntities = previsaoRepository.findByMunicipioIgnoreCaseAndDataPrevisaoAfterOrderByDataPrevisaoAsc(normalizedQuery, LocalDate.now().minusDays(1));
        if (previsaoEntities.isEmpty()) {
            previsaoEntities = previsaoRepository.findByDataPrevisaoAfterOrderByDataPrevisaoAsc(LocalDate.now().minusDays(1))
                    .stream()
                    .filter(p -> CityNameNormalizer.normalize(p.getMunicipio()).equals(normalizedQuery))
                    .collect(Collectors.toList());
        }

        var previsoes = previsaoEntities.stream()
                .limit(3)
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

        return new DashboardDTO(normalizedQuery, alertas, previsoes);
    }

    public List<AlertDTO> getLatestNationalAlerts() {
        log.info("📊 Buscando últimos 50 alertas nacionais");

        return alertRepository.findTop50ByOrderByDataHoraAlertaDesc()
                .stream()
                .map(alert -> new AlertDTO(
                        alert.getId(),
                        alert.getMunicipio(),
                        alert.getEstado(),
                        alert.getTipoAlerta(),
                        alert.getMensagem(),
                        alert.getDataHoraAlerta()
                ))
                .collect(Collectors.toList());
    }
}