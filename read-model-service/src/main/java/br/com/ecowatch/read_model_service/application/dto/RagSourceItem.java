package br.com.ecowatch.read_model_service.application.dto;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Fonte e evidência factual recuperada pelo RAG")
public class RagSourceItem {
    private Long id;
    private String titulo;
    private String resumo;
    private String fonte;
    private String url;
    private Instant dataHora;
    private NewsCategory categoria;
    private String municipio;
    private String estado;
    private Double similaridade;
}
