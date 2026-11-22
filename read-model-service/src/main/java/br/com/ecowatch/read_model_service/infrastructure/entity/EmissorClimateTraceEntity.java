package br.com.ecowatch.read_model_service.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "emissores_climatetrace",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_asset_gas", columnNames = {"asset_id", "gas"})
        },
        indexes = {
                @Index(name = "idx_emissores_gas", columnList = "gas"),
                @Index(name = "idx_emissores_setor", columnList = "setor"),
                @Index(name = "idx_emissores_emissao", columnList = "emissao_t")
        })
@Getter
@Setter
@NoArgsConstructor
public class EmissorClimateTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(nullable = false, length = 100)
    private String setor;

    @Column(name = "sub_setor", length = 100)
    private String subSetor;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, length = 20)
    private String gas;

    @Column(name = "emissao_t", nullable = false)
    private Double emissaoT;

    @Column(nullable = false)
    private Integer ano;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
