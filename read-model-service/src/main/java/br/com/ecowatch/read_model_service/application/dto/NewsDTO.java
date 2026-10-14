package br.com.ecowatch.read_model_service.application.dto;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO para transporte de notícias e comunicados de desastres ambientais.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewsDTO {

    private Long id;
    private String titulo;
    private String resumo;
    private String fonte;
    private String url;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant dataHora;

    private NewsCategory categoria;
    private String municipio;
    private String estado;
}
