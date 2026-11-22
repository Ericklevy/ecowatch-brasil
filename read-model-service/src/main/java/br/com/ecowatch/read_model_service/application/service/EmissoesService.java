package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.EmissoesDTO.*;
import br.com.ecowatch.read_model_service.infrastructure.entity.EmissaoMunicipioEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.EmissaoMunicipioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmissoesService {

    private final EmissaoMunicipioRepository repository;
    private final br.com.ecowatch.read_model_service.infrastructure.persistence.EmissorClimateTraceRepository climateTraceRepository;

    public List<RankingMunicipioItem> getRanking(String gasParam, String ufParam, Integer anoParam, Integer limiteParam) {
        String gas = normalizarGas(gasParam);
        String uf = (ufParam != null && !ufParam.isBlank() && !"ALL".equalsIgnoreCase(ufParam)) ? ufParam.trim().toUpperCase() : null;
        int limite = (limiteParam != null && limiteParam > 0) ? Math.min(limiteParam, 100) : 10;

        int ano = (anoParam != null && anoParam > 1970) ? anoParam : resolverUltimoAno(gas);

        List<Object[]> rows = repository.findRankingMunicipios(gas, uf, ano);
        List<RankingMunicipioItem> result = new ArrayList<>();

        for (int i = 0; i < Math.min(rows.size(), limite); i++) {
            Object[] r = rows.get(i);
            String codigoIbge = (String) r[0];
            String municipio = (String) r[1];
            String estadoUf = (String) r[2];
            Integer anoRes = (Integer) r[3];
            String gasRes = (String) r[4];
            Double total = r[5] != null ? ((Number) r[5]).doubleValue() : 0.0;

            // Busca quebra por setor
            List<Object[]> setoresRows = repository.findSetoresPorMunicipio(codigoIbge, gas, ano);
            Map<String, Double> quebra = new LinkedHashMap<>();
            String setorPrincipal = "Outros";
            double maxVal = 0.0;

            for (Object[] sr : setoresRows) {
                String set = (String) sr[0];
                Double val = sr[1] != null ? ((Number) sr[1]).doubleValue() : 0.0;
                quebra.put(set, val);
                if (val > maxVal) {
                    maxVal = val;
                    setorPrincipal = set;
                }
            }

            double percentual = total > 0 ? (maxVal / total) * 100.0 : 0.0;
            String explicacao = gerarExplicacaoSetor(setorPrincipal, estadoUf, percentual);

            result.add(RankingMunicipioItem.builder()
                    .codigoIbge(codigoIbge)
                    .municipio(municipio)
                    .uf(estadoUf)
                    .ano(anoRes)
                    .gas(gasRes)
                    .emissaoTotal(total)
                    .setorPrincipal(setorPrincipal)
                    .percentualSetorPrincipal(Math.round(percentual * 10.0) / 10.0)
                    .explicacaoSetor(explicacao)
                    .quebraPorSetor(quebra)
                    .build());
        }

        return result;
    }

    public List<EmissaoEstadoItem> getEstados(String gasParam, Integer anoParam) {
        String gas = normalizarGas(gasParam);
        int ano = (anoParam != null && anoParam > 1970) ? anoParam : resolverUltimoAno(gas);

        List<Object[]> rows = repository.sumEmissoesPorEstado(gas, ano);
        List<EmissaoEstadoItem> result = new ArrayList<>();

        for (Object[] r : rows) {
            String uf = (String) r[0];
            Integer anoRes = (Integer) r[1];
            String gasRes = (String) r[2];
            Double total = r[3] != null ? ((Number) r[3]).doubleValue() : 0.0;

            List<Object[]> setoresRows = repository.findSetoresPorEstado(uf, gas, ano);
            Map<String, Double> quebra = new LinkedHashMap<>();
            String setorPrincipal = "Outros";
            double maxVal = 0.0;

            for (Object[] sr : setoresRows) {
                String set = (String) sr[0];
                Double val = sr[1] != null ? ((Number) sr[1]).doubleValue() : 0.0;
                quebra.put(set, val);
                if (val > maxVal) {
                    maxVal = val;
                    setorPrincipal = set;
                }
            }

            double percentual = total > 0 ? (maxVal / total) * 100.0 : 0.0;
            String explicacao = gerarExplicacaoSetor(setorPrincipal, uf, percentual);

            result.add(EmissaoEstadoItem.builder()
                    .uf(uf)
                    .ano(anoRes)
                    .gas(gasRes)
                    .emissaoTotal(total)
                    .setorPrincipal(setorPrincipal)
                    .percentualSetorPrincipal(Math.round(percentual * 10.0) / 10.0)
                    .explicacaoSetor(explicacao)
                    .quebraPorSetor(quebra)
                    .build());
        }

        return result;
    }

    public MunicipioDetalheDTO getMunicipioDetalhe(String nome, String gasParam) {
        String gas = normalizarGas(gasParam);
        List<EmissaoMunicipioEntity> entries = repository.findByMunicipioIgnoreCaseAndGasOrderByAnoAsc(nome, gas);

        if (entries.isEmpty()) {
            // Tenta fallback com like ou qualquer gás
            entries = repository.findByMunicipioIgnoreCaseOrderByAnoAsc(nome);
        }

        if (entries.isEmpty()) {
            return null;
        }

        String codigoIbge = entries.get(0).getCodigoIbge();
        String munNome = entries.get(0).getMunicipio();
        String uf = entries.get(0).getUf();

        // Agrupa por ano
        Map<Integer, List<EmissaoMunicipioEntity>> porAno = entries.stream()
                .collect(Collectors.groupingBy(EmissaoMunicipioEntity::getAno));

        List<SerieAnoDTO> serie = new ArrayList<>();
        Map<String, Double> acumuladoSetor = new HashMap<>();

        for (Map.Entry<Integer, List<EmissaoMunicipioEntity>> entry : porAno.entrySet()) {
            int ano = entry.getKey();
            Map<String, Double> quebra = new LinkedHashMap<>();
            double totalAno = 0.0;

            for (EmissaoMunicipioEntity e : entry.getValue()) {
                quebra.put(e.getSetor(), quebra.getOrDefault(e.getSetor(), 0.0) + e.getEmissaoT());
                acumuladoSetor.put(e.getSetor(), acumuladoSetor.getOrDefault(e.getSetor(), 0.0) + e.getEmissaoT());
                totalAno += e.getEmissaoT();
            }

            serie.add(SerieAnoDTO.builder()
                    .ano(ano)
                    .emissaoTotal(Math.round(totalAno * 100.0) / 100.0)
                    .quebraPorSetor(quebra)
                    .build());
        }

        serie.sort(Comparator.comparing(SerieAnoDTO::getAno));

        String setorDominante = acumuladoSetor.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Outros");

        String explicacao = gerarExplicacaoSetor(setorDominante, uf, null);

        return MunicipioDetalheDTO.builder()
                .codigoIbge(codigoIbge)
                .municipio(munNome)
                .uf(uf)
                .gas(gas)
                .setorDominante(setorDominante)
                .explicacaoSetorDominante(explicacao)
                .serieHistorica(serie)
                .build();
    }

    public List<FonteEmissaoDTO> getFontesEmissao(String gasParam, String setorParam, Integer limiteParam) {
        String gas = normalizarGas(gasParam);
        String setor = (setorParam != null && !setorParam.isBlank() && !"ALL".equalsIgnoreCase(setorParam)) ? setorParam.trim() : null;
        int limite = (limiteParam != null && limiteParam > 0) ? Math.min(limiteParam, 200) : 100;

        List<br.com.ecowatch.read_model_service.infrastructure.entity.EmissorClimateTraceEntity> entidades =
                climateTraceRepository.findTopEmissores(gas, setor);

        return entidades.stream()
                .limit(limite)
                .map(e -> FonteEmissaoDTO.builder()
                        .assetId(e.getAssetId())
                        .nome(e.getNome())
                        .setor(e.getSetor())
                        .subSetor(e.getSubSetor())
                        .setorRotulo(ClimateTraceIngestionService.traduzirSetor(e.getSetor()))
                        .latitude(e.getLatitude())
                        .longitude(e.getLongitude())
                        .gas(e.getGas())
                        .emissaoAnualT(Math.round(e.getEmissaoT() * 100.0) / 100.0)
                        .ano(e.getAno())
                        .credito("Climate TRACE (CC BY 4.0)")
                        .build())
                .collect(Collectors.toList());
    }

    private int resolverUltimoAno(String gas) {
        Integer ano = repository.findMaxAnoByGas(gas);
        if (ano != null && ano > 1970) return ano;
        Integer maxGeral = repository.findMaxAno();
        return (maxGeral != null && maxGeral > 1970) ? maxGeral : 2023;
    }

    private String normalizarGas(String gas) {
        if (gas == null || gas.isBlank()) return "CO2e";
        String g = gas.trim().toUpperCase();
        if (g.contains("CH4") || g.contains("METAN")) return "CH4";
        if (g.contains("CO2E") || g.contains("EQUIV")) return "CO2e";
        if (g.equals("CO2") || g.contains("CARBONO")) return "CO2";
        if (g.contains("N2O")) return "N2O";
        return "CO2e";
    }

    private String gerarExplicacaoSetor(String setor, String uf, Double pct) {
        String pctTexto = (pct != null && pct > 0) ? String.format(" (%.1f%%)", pct) : "";
        String prefixo = (uf != null && !uf.isBlank()) ? "Em " + uf + ", a maior parte vem de " : "A principal fonte de emissões é ";

        return switch (setor) {
            case "Mudança de uso da terra" -> prefixo + "Mudança de uso da terra" + pctTexto + " (desmatamento e conversão de florestas/biomas nativos).";
            case "Agropecuária" -> prefixo + "Agropecuária" + pctTexto + " (pecuária bovina, fermentação entérica e manejo de pastagens).";
            case "Energia" -> prefixo + "Energia" + pctTexto + " (queima de combustíveis fósseis em termelétricas, indústrias e transportes).";
            case "Processos industriais" -> prefixo + "Processos industriais" + pctTexto + " (produção de cimento, siderurgia e química pesada).";
            case "Resíduos" -> prefixo + "Resíduos" + pctTexto + " (decomposição orgânica em aterros sanitários e tratamento de efluentes).";
            default -> prefixo + setor + pctTexto + ".";
        };
    }
}
