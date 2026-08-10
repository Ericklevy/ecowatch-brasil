package br.com.ecowatch.read_model_service.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertDTO {
    private Long id;
    private String municipio;
    private String estado;
    private String tipoAlerta;
    private String mensagem;
    private LocalDateTime dataHoraAlerta;
}