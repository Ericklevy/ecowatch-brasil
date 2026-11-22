package br.com.ecowatch.read_model_service.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class EmissoesDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankingMunicipioItem {
        private String codigoIbge;
        private String municipio;
        private String uf;
        private Integer ano;
        private String gas;
        private Double emissaoTotal;
        private String setorPrincipal;
        private Double percentualSetorPrincipal;
        private String explicacaoSetor;
        private Map<String, Double> quebraPorSetor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmissaoEstadoItem {
        private String uf;
        private Integer ano;
        private String gas;
        private Double emissaoTotal;
        private String setorPrincipal;
        private Double percentualSetorPrincipal;
        private String explicacaoSetor;
        private Map<String, Double> quebraPorSetor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MunicipioDetalheDTO {
        private String codigoIbge;
        private String municipio;
        private String uf;
        private String gas;
        private String setorDominante;
        private String explicacaoSetorDominante;
        private List<SerieAnoDTO> serieHistorica;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerieAnoDTO {
        private Integer ano;
        private Double emissaoTotal;
        private Map<String, Double> quebraPorSetor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FonteEmissaoDTO {
        private Long assetId;
        private String nome;
        private String setor;
        private String subSetor;
        private String setorRotulo;
        private Double latitude;
        private Double longitude;
        private String gas;
        private Double emissaoAnualT;
        private Integer ano;
        private String credito;
    }
}
