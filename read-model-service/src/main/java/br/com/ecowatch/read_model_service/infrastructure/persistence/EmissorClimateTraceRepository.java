package br.com.ecowatch.read_model_service.infrastructure.persistence;

import br.com.ecowatch.read_model_service.infrastructure.entity.EmissorClimateTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmissorClimateTraceRepository extends JpaRepository<EmissorClimateTraceEntity, Long> {

    Optional<EmissorClimateTraceEntity> findByAssetIdAndGas(Long assetId, String gas);

    List<EmissorClimateTraceEntity> findByGasIgnoreCaseOrderByEmissaoTDesc(String gas);

    @Query("""
        SELECT e FROM EmissorClimateTraceEntity e
        WHERE (:gas IS NULL OR UPPER(e.gas) = UPPER(CAST(:gas AS string)))
          AND (:setor IS NULL 
               OR LOWER(e.setor) LIKE LOWER(CONCAT('%', CAST(:setor AS string), '%')) 
               OR LOWER(e.subSetor) LIKE LOWER(CONCAT('%', CAST(:setor AS string), '%')))
        ORDER BY e.emissaoT DESC
    """)
    List<EmissorClimateTraceEntity> findTopEmissores(
            @Param("gas") String gas,
            @Param("setor") String setor
    );
}
