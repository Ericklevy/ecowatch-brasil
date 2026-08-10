package br.com.ecowatch.read_model_service.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrevisaoDTO {
    private String municipio;
    private LocalDate dataPrevisao;
    private Double temperaturaPrevista;
    private Double precipitacaoPrevista;
    private Double umidadePrevista;
    private Integer riscoIncendio;
    private Integer riscoDengue;
}