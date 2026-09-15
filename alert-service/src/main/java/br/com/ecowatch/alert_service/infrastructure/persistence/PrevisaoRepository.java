package br.com.ecowatch.alert_service.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PrevisaoRepository extends JpaRepository<PrevisaoEntity, Long> {
    List<PrevisaoEntity> findByMunicipioOrderByDataPrevisaoDesc(String municipio);
    List<PrevisaoEntity> findByMunicipioAndDataPrevisao(String municipio, LocalDate dataPrevisao);
}
