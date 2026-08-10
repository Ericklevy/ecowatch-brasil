package br.com.ecowatch.read_model_service.infrastructure.persistence;

import br.com.ecowatch.read_model_service.infrastructure.entity.PrevisaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PrevisaoRepository extends JpaRepository<PrevisaoEntity, Long> {
    List<PrevisaoEntity> findByMunicipioOrderByDataPrevisaoDesc(String municipio);
    List<PrevisaoEntity> findByDataPrevisaoAfterOrderByDataPrevisaoAsc(LocalDate data);
}