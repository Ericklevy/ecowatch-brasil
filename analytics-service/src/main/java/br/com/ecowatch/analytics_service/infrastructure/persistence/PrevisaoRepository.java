package br.com.ecowatch.analytics_service.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PrevisaoRepository extends JpaRepository<PrevisaoEntity, Long> {

    List<PrevisaoEntity> findByMunicipioAndDataPrevisaoAfter(String municipio, LocalDate data);

    List<PrevisaoEntity> findByMunicipioOrderByDataPrevisaoDesc(String municipio);

    List<PrevisaoEntity> findByMunicipioInAndDataPrevisaoIn(java.util.Collection<String> municipios, java.util.Collection<LocalDate> datas);
}