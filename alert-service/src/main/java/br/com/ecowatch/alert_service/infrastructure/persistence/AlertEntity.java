package br.com.ecowatch.alert_service.infrastructure.persistence;

import br.com.ecowatch.alert_service.domain.model.AlertType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    private String mensagem;
    private LocalDateTime dataHoraLeitura; // timestamp da leitura original
    private LocalDateTime dataHoraAlerta;  // momento do processamento

    public AlertEntity(String municipio, String estado, AlertType tipoAlerta, String mensagem, LocalDateTime timestampLeitura) {
        this.municipio = municipio;
        this.estado = estado;
        this.tipoAlerta = tipoAlerta;
        this.mensagem = mensagem;
        this.dataHoraLeitura = timestampLeitura;
        this.dataHoraAlerta = LocalDateTime.now();
    }
}