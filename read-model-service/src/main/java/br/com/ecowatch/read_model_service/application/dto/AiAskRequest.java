package br.com.ecowatch.read_model_service.application.dto;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requisição de consulta ao Assistente IA RAG do EcoWatch")
public class AiAskRequest {

    @NotBlank(message = "A pergunta não pode estar vazia")
    @Schema(description = "Pergunta em linguagem natural", example = "Qual a situação dos rios no Rio Grande do Sul?")
    private String pergunta;

    @Schema(description = "Filtrar por categoria específica (opcional)", example = "FLOOD")
    private NewsCategory categoria;

    @Schema(description = "Filtrar por UF de estado (opcional)", example = "RS")
    private String estado;

    @Schema(description = "Quantidade máxima de matérias como contexto", defaultValue = "4")
    private Integer topK;
}
