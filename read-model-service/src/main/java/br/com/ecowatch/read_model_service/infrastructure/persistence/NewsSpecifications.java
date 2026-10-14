package br.com.ecowatch.read_model_service.infrastructure.persistence;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import br.com.ecowatch.read_model_service.infrastructure.entity.NewsEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Especificações JPA dinâmicas para filtragem de notícias ambientais.
 */
public class NewsSpecifications {

    private NewsSpecifications() {}

    public static Specification<NewsEntity> withFilters(NewsCategory categoria, String estado, String municipio) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (categoria != null) {
                predicates.add(cb.equal(root.get("categoria"), categoria));
            }
            if (estado != null && !estado.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("estado")), estado.trim().toUpperCase()));
            }
            if (municipio != null && !municipio.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("municipio")), "%" + municipio.trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
