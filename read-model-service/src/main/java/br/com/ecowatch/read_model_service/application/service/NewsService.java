package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.NewsDTO;
import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import br.com.ecowatch.read_model_service.infrastructure.entity.NewsEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.NewsRepository;
import br.com.ecowatch.read_model_service.infrastructure.persistence.NewsSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;
    private final NewsIngestionService newsIngestionService;

    @Transactional(readOnly = true)
    public Page<NewsDTO> getNews(NewsCategory categoria, String estado, String municipio, Pageable pageable) {
        String cleanEstado = (estado != null && !estado.isBlank()) ? estado.trim().toUpperCase() : null;
        String cleanMunicipio = (municipio != null && !municipio.isBlank()) ? municipio.trim() : null;

        Specification<NewsEntity> spec = NewsSpecifications.withFilters(categoria, cleanEstado, cleanMunicipio);
        Page<NewsEntity> entities = newsRepository.findAll(spec, pageable);
        return entities.map(this::toDTO);
    }

    public int syncNews() {
        return newsIngestionService.syncAllSources();
    }

    private NewsDTO toDTO(NewsEntity entity) {
        return NewsDTO.builder()
                .id(entity.getId())
                .titulo(entity.getTitulo())
                .resumo(entity.getResumo())
                .fonte(entity.getFonte())
                .url(entity.getUrl())
                .dataHora(entity.getDataHora())
                .categoria(entity.getCategoria())
                .municipio(entity.getMunicipio())
                .estado(entity.getEstado())
                .build();
    }
}
