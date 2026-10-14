package br.com.ecowatch.read_model_service.infrastructure.persistence;

import br.com.ecowatch.read_model_service.infrastructure.entity.NewsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<NewsEntity, Long>, JpaSpecificationExecutor<NewsEntity> {

    boolean existsByUrl(String url);

    Optional<NewsEntity> findByUrl(String url);
}
