package br.com.ecowatch.analytics_service.infrastructure.persistence;

import br.com.ecowatch.analytics_service.util.CityNameNormalizer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "previsoes")
@Getter
@Setter
@NoArgsConstructor
public class PrevisaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "municipio", nullable = false)
    private String municipio;

    @Column(name = "data_previsao", nullable = false)
    private LocalDate dataPrevisao;

    @Column(name = "temperatura_prevista")
    private Double temperaturaPrevista;

    @Column(name = "precipitacao_prevista")
    private Double precipitacaoPrevista;

    @Column(name = "umidade_prevista")
    private Double umidadePrevista;

    @Column(name = "risco_incendio")
    private Integer riscoIncendio;

    @Column(name = "risco_dengue")
    private Integer riscoDengue;

    @Column(name = "data_geracao")
    private LocalDateTime dataGeracao;

    public PrevisaoEntity(String municipio, LocalDate dataPrevisao, Double temperaturaPrevista,
                          Double precipitacaoPrevista, Double umidadePrevista,
                          Integer riscoIncendio, Integer riscoDengue) {
        this.municipio = CityNameNormalizer.normalize(municipio);
        this.dataPrevisao = dataPrevisao;
        this.temperaturaPrevista = temperaturaPrevista;
        this.precipitacaoPrevista = precipitacaoPrevista;
        this.umidadePrevista = umidadePrevista;
        this.riscoIncendio = riscoIncendio;
        this.riscoDengue = riscoDengue;
        this.dataGeracao = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
    }

    public void setMunicipio(String municipio) {
        this.municipio = CityNameNormalizer.normalize(municipio);
    }
}