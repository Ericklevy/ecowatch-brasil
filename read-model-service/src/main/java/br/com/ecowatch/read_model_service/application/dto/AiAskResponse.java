package br.com.ecowatch.read_model_service.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta sintetizada pela IA baseada nas notícias do EcoWatch")
public class AiAskResponse {
    @Schema(description = "Pergunta original do usuário")
    private String pergunta;

    @Schema(description = "Resposta fundamentada gerada pela IA")
    private String resposta;

    @Schema(description = "Fontes e notícias oficiais utilizadas na fundamentação")
    private List<RagSourceItem> fontes;

    @Schema(description = "Total de notícias relevantes encontradas na busca vetorial")
    private int totalFontesEncontradas;

    @Schema(description = "Motor de IA utilizado", example = "gemini-2.5-flash")
    private String modelo;

    @Schema(description = "Tempo total de processamento em milissegundos")
    private long tempoRespostaMs;

    @Schema(description = "True quando a cota gratuita da IA acabou e a resposta foi montada só com os dados salvos")
    private boolean limiteAtingido;

    @Schema(description = "Capitais cujos dados medidos (alertas e previsões) entraram no contexto")
    private List<String> cidadesConsultadas;

    @Schema(description = "Período das notícias usadas, ex.: 18/11 a 21/11")
    private String periodoFontes;
}
