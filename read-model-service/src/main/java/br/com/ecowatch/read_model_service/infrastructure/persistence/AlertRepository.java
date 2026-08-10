package br.com.ecowatch.read_model_service.infrastructure.persistence;

import br.com.ecowatch.read_model_service.infrastructure.entity.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, Long> {
    List<AlertEntity> findByMunicipioOrderByDataHoraAlertaDesc(String municipio);
    List<AlertEntity> findAllByOrderByDataHoraAlertaDesc();
}