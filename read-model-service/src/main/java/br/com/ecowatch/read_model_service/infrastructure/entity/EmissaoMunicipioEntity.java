package br.com.ecowatch.read_model_service.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "emissoes_municipio", indexes = {
        @Index(name = "idx_emissoes_gas_uf_ano", columnList = "gas, uf, ano"),
        @Index(name = "idx_emissoes_mun_ano", columnList = "municipio, ano")
})
@Getter
@Setter
@NoArgsConstructor
public class EmissaoMunicipioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_ibge", nullable = false, length = 10)
    private String codigoIbge;

    @Column(nullable = false, length = 150)
    private String municipio;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(nullable = false)
    private Integer ano;

    @Column(nullable = false, length = 20)
    private String gas;

    @Column(nullable = false, length = 100)
    private String setor;

    @Column(name = "emissao_t", nullable = false)
    private Double emissaoT;
}
