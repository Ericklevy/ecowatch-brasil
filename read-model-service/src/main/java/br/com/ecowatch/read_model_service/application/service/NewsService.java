package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.NewsDTO;
import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import br.com.ecowatch.read_model_service.infrastructure.entity.NewsEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.NewsRepository;
import br.com.ecowatch.read_model_service.infrastructure.persistence.NewsSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;
    private final NewsClassifier newsClassifier;
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

    /**
     * Remove do banco matérias irrelevantes (ruído político, eleições, curiosidades)
     * e reclassifica matérias antigas para suas categorias semânticas corretas.
     */
    public int purgeIrrelevantNews() {
        return newsIngestionService.purgeIrrelevantExistingNews();
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
