package br.com.ecowatch.read_model_service.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Status operacional da infraestrutura de IA e banco vetorial")
public class AiStatusResponse {
    @Schema(description = "Indica se o pgvector está ativo no PostgreSQL")
    private boolean pgvectorAtivo;

    @Schema(description = "Total de notícias ambientais disponíveis no banco")
    private long totalNoticias;

    @Schema(description = "Total de embeddings calculados e indexados para RAG")
    private long totalEmbeddings;

    @Schema(description = "Indica se a chave da API do Gemini está configurada")
    private boolean geminiConfigurado;

    @Schema(description = "Nome do modelo de IA configurado")
    private String modeloPadrao;

    @Schema(description = "Motivo quando o pgvector não está ativo (para diagnóstico)")
    private String diagnostico;
}
