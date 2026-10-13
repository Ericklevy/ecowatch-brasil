package br.com.ecowatch.read_model_service.infrastructure.entity;

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
    private String tipoAlerta;
    @Column(columnDefinition = "TEXT")
    private String mensagem;
    private LocalDateTime dataHoraAlerta;
}