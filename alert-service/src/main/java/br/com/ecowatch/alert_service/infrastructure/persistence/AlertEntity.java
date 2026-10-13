package br.com.ecowatch.alert_service.infrastructure.persistence;

import br.com.ecowatch.alert_service.domain.model.AlertType;
import br.com.ecowatch.alert_service.util.CityNameNormalizer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "alertas_gerados")
@Getter
@Setter
@NoArgsConstructor
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String municipio;
    private String estado;

    @Enumerated(EnumType.STRING)
    private AlertType tipoAlerta; // agora usa o enum

    @Column(columnDefinition = "TEXT")
    private String mensagem;
    private LocalDateTime dataHoraLeitura; // timestamp da leitura original
    private LocalDateTime dataHoraAlerta;  // momento do processamento

    public AlertEntity(String municipio, String estado, AlertType tipoAlerta, String mensagem, LocalDateTime timestampLeitura) {
        this.municipio = CityNameNormalizer.normalize(municipio);
        this.estado = estado;
        this.tipoAlerta = tipoAlerta;
        this.mensagem = mensagem;
        this.dataHoraLeitura = timestampLeitura;
        this.dataHoraAlerta = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
    }

    public void setMunicipio(String municipio) {
        this.municipio = CityNameNormalizer.normalize(municipio);
    }
}