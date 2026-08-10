package br.com.ecowatch.read_model_service.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private String municipio;
    private List<AlertDTO> alertas;
    private List<PrevisaoDTO> previsoes;
}