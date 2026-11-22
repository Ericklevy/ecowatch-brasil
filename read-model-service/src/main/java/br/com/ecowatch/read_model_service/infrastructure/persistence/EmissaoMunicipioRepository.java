package br.com.ecowatch.read_model_service.infrastructure.persistence;

import br.com.ecowatch.read_model_service.infrastructure.entity.EmissaoMunicipioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmissaoMunicipioRepository extends JpaRepository<EmissaoMunicipioEntity, Long> {

    @Query("SELECT MAX(e.ano) FROM EmissaoMunicipioEntity e")
    Integer findMaxAno();

    @Query("SELECT MAX(e.ano) FROM EmissaoMunicipioEntity e WHERE e.gas = :gas")
    Integer findMaxAnoByGas(@Param("gas") String gas);

    @Query("SELECT DISTINCT e.gas FROM EmissaoMunicipioEntity e")
    List<String> findDistinctGases();

    List<EmissaoMunicipioEntity> findByMunicipioIgnoreCaseOrderByAnoAsc(String municipio);

    List<EmissaoMunicipioEntity> findByMunicipioIgnoreCaseAndGasOrderByAnoAsc(String municipio, String gas);

    @Query("""
        SELECT e.uf as uf, e.ano as ano, e.gas as gas, SUM(e.emissaoT) as total
        FROM EmissaoMunicipioEntity e
        WHERE e.gas = :gas AND e.ano = :ano
        GROUP BY e.uf, e.ano, e.gas
        ORDER BY total DESC
    """)
    List<Object[]> sumEmissoesPorEstado(@Param("gas") String gas, @Param("ano") Integer ano);

    @Query("""
        SELECT e.codigoIbge as codigoIbge, e.municipio as municipio, e.uf as uf, e.ano as ano, e.gas as gas, SUM(e.emissaoT) as total
        FROM EmissaoMunicipioEntity e
        WHERE e.gas = :gas AND e.ano = :ano AND (:uf IS NULL OR e.uf = :uf)
        GROUP BY e.codigoIbge, e.municipio, e.uf, e.ano, e.gas
        ORDER BY total DESC
    """)
    List<Object[]> findRankingMunicipios(@Param("gas") String gas, @Param("uf") String uf, @Param("ano") Integer ano);

    @Query("""
        SELECT e.setor, SUM(e.emissaoT)
        FROM EmissaoMunicipioEntity e
        WHERE e.codigoIbge = :codigoIbge AND e.gas = :gas AND e.ano = :ano
        GROUP BY e.setor
        ORDER BY SUM(e.emissaoT) DESC
    """)
    List<Object[]> findSetoresPorMunicipio(@Param("codigoIbge") String codigoIbge, @Param("gas") String gas, @Param("ano") Integer ano);

    @Query("""
        SELECT e.setor, SUM(e.emissaoT)
        FROM EmissaoMunicipioEntity e
        WHERE e.uf = :uf AND e.gas = :gas AND e.ano = :ano
        GROUP BY e.setor
        ORDER BY SUM(e.emissaoT) DESC
    """)
    List<Object[]> findSetoresPorEstado(@Param("uf") String uf, @Param("gas") String gas, @Param("ano") Integer ano);
}
