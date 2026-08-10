package br.com.ecowatch.read_model_service.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "previsoes")
@Getter
@Setter
@NoArgsConstructor
public class PrevisaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
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
}