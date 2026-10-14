package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Classificador temático e extrator de localização e resumos de notícias ambientais.
 * Aplica filtro sanitizador anti-ruído (rejeita política, eleições e trivialidades)
 * e exige relevância semântica estrita para monitoramento de desastres climáticos.
 */
@Component
public class NewsClassifier {

    public record LocationInfo(String municipio, String estado) {}

    private static final Map<String, String> STATE_BY_NAME = new LinkedHashMap<>();
    private static final Map<String, String> CAPITAL_TO_STATE = new LinkedHashMap<>();

    // Termos de rejeição obrigatória (Blacklist de Ruído Político e Trivialidades)
    private static final List<String> BLACKLIST_TERMS = List.of(
            "urna", "urnas", "eleicao", "eleicoes", "2o turno", "2º turno", "segundo turno",
            "candidato", "candidatos", "candidata", "partido", "partidos", "stf", "voto", "votos",
            "votacao", "tse", "parlamentar", "deputado", "deputados", "senador", "senadores",
            "plenario", "tribunal superior", "marco temporal", "lixo eletronico", "florada do ipe",
            "tomando um solzinho", "tomando solzinho", "ninhos nunca vistos", "zoologico", "parque das aves",
            "filhote de", "receita federal", "imposto de renda", "inflacao", "selic", "copom",
            "bolsa familia", "futebol", "campeonato", "copa do brasil", "libertadores", "brasileirao"
    );

    // Palavras-chave obrigatórias do domínio (Whitelist de Relevância de Desastres/Clima)
    private static final List<String> DOMAIN_WHITELIST = List.of(
            "fogo", "queimada", "queimadas", "incendio", "incendios", "fumaca", "chamas",
            "rio", "rios", "cheia", "cheias", "enchente", "enchentes", "cota", "cota de alerta",
            "cota de inundacao", "vazante", "chuva", "chuvas", "tempestade", "tempestades",
            "temporal", "temporais", "alagamento", "alagamentos", "enxurrada", "granizo",
            "dengue", "aedes", "arbovirose", "chikungunya", "zika", "desmatamento", "desmatar",
            "corte raso", "deter", "seca", "secas", "estiagem", "calor extremo", "ar seco",
            "baixa umidade", "defesa civil", "cemaden", "inpe", "infodengue"
    );

    static {
        STATE_BY_NAME.put("acre", "AC");
        STATE_BY_NAME.put("alagoas", "AL");
        STATE_BY_NAME.put("amapa", "AP");
        STATE_BY_NAME.put("amazonas", "AM");
        STATE_BY_NAME.put("bahia", "BA");
        STATE_BY_NAME.put("ceara", "CE");
        STATE_BY_NAME.put("distrito federal", "DF");
        STATE_BY_NAME.put("espirito santo", "ES");
        STATE_BY_NAME.put("goias", "GO");
        STATE_BY_NAME.put("maranhao", "MA");
        STATE_BY_NAME.put("mato grosso do sul", "MS");
        STATE_BY_NAME.put("mato grosso", "MT");
        STATE_BY_NAME.put("minas gerais", "MG");
        STATE_BY_NAME.put("para", "PA");
        STATE_BY_NAME.put("paraiba", "PB");
        STATE_BY_NAME.put("parana", "PR");
        STATE_BY_NAME.put("pernambuco", "PE");
        STATE_BY_NAME.put("piaui", "PI");
        STATE_BY_NAME.put("rio de janeiro", "RJ");
        STATE_BY_NAME.put("rio grande do norte", "RN");
        STATE_BY_NAME.put("rio grande do sul", "RS");
        STATE_BY_NAME.put("rondonia", "RO");
        STATE_BY_NAME.put("roraima", "RR");
        STATE_BY_NAME.put("santa catarina", "SC");
        STATE_BY_NAME.put("sao paulo", "SP");
        STATE_BY_NAME.put("sergipe", "SE");
        STATE_BY_NAME.put("tocantins", "TO");

        CAPITAL_TO_STATE.put("rio branco", "AC");
        CAPITAL_TO_STATE.put("maceio", "AL");
        CAPITAL_TO_STATE.put("macapa", "AP");
        CAPITAL_TO_STATE.put("manaus", "AM");
        CAPITAL_TO_STATE.put("salvador", "BA");
        CAPITAL_TO_STATE.put("fortaleza", "CE");
        CAPITAL_TO_STATE.put("brasilia", "DF");
        CAPITAL_TO_STATE.put("vitoria", "ES");
        CAPITAL_TO_STATE.put("goiania", "GO");
        CAPITAL_TO_STATE.put("sao luis", "MA");
        CAPITAL_TO_STATE.put("cuiaba", "MT");
        CAPITAL_TO_STATE.put("campo grande", "MS");
        CAPITAL_TO_STATE.put("belo horizonte", "MG");
        CAPITAL_TO_STATE.put("belem", "PA");
        CAPITAL_TO_STATE.put("joao pessoa", "PB");
        CAPITAL_TO_STATE.put("curitiba", "PR");
        CAPITAL_TO_STATE.put("recife", "PE");
        CAPITAL_TO_STATE.put("teresina", "PI");
        CAPITAL_TO_STATE.put("natal", "RN");
        CAPITAL_TO_STATE.put("porto alegre", "RS");
        CAPITAL_TO_STATE.put("porto velho", "RO");
        CAPITAL_TO_STATE.put("boa vista", "RR");
        CAPITAL_TO_STATE.put("florianopolis", "SC");
        CAPITAL_TO_STATE.put("aracaju", "SE");
        CAPITAL_TO_STATE.put("palmas", "TO");
    }

    private static final List<String> UF_LIST = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA",
            "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN",
            "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    );

    /**
     * Verifica se o texto contém algum termo proibido da Blacklist.
     */
    public boolean isBlacklisted(String text) {
        if (text == null || text.isBlank()) return true;
        for (String term : BLACKLIST_TERMS) {
            if (Pattern.compile("\\b" + Pattern.quote(term) + "\\b").matcher(text).find() || text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se o texto atende à exigência mínima de conter palavras do domínio de desastres/clima.
     */
    public boolean hasDomainRelevance(String text) {
        if (text == null || text.isBlank()) return false;
        for (String term : DOMAIN_WHITELIST) {
            if (Pattern.compile("\\b" + Pattern.quote(term) + "\\b").matcher(text).find() || text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Classifica a notícia com base no título e descrição.
     * Rejeita ruído e retorna Optional.empty() se não atender às regras estritas.
     */
    public Optional<NewsCategory> classify(String title, String description, boolean isEnvironmentalFeed) {
        String rawCombined = (title != null ? title : "") + " " + (description != null ? description : "");
        String text = stripAccents(rawCombined.toLowerCase());

        // 1. Filtro Sanitizador de Ingestão: Descarte imediato de ruído político e trivialidades
        if (isBlacklisted(text)) {
            return Optional.empty();
        }

        // 2. Exigência de relevância de domínio (Whitelist)
        if (!hasDomainRelevance(text)) {
            return Optional.empty();
        }

        // 3. Classificação Semântica Estrita

        // DENGUE
        if (text.contains("dengue") || text.contains("aedes") || text.contains("chikungunya") ||
            text.contains("zika") || text.contains("fumace") || text.contains("arbovirose") ||
            text.contains("infodengue")) {
            return Optional.of(NewsCategory.DENGUE);
        }

        // FIRE (fogo, queimada, incêndio, fumaça, chamas, INPE associado a calor)
        if (text.contains("queimada") || text.contains("incendio") || text.contains("foco de calor") ||
            text.contains("focos de calor") || text.contains("fumaca") || text.contains("chamas") ||
            text.contains("brigadista") || text.contains("pantanal em chamas") ||
            text.contains("amazonia em chamas") || text.contains("combate ao fogo")) {
            return Optional.of(NewsCategory.FIRE);
        }

        // FLOOD (rio, rios, enchente, cheia, inundação, vazante, cota, Guaíba, Rio Negro)
        if (text.contains("enchente") || text.contains("inundacao") || text.contains("alagamento") ||
            text.contains("deslizamento") || text.contains("transbord") || text.contains("enxurrada") ||
            text.contains("cota de inundacao") || text.contains("cota de alerta") || text.contains("desabrigado") ||
            text.contains("desabamento") || text.contains("dique") || text.contains("cheia do rio") ||
            text.contains("cheias") || text.contains("vazante") || text.contains("rio guaiba") ||
            text.contains("rio negro") || (text.contains("rio ") && (text.contains("sobe") || text.contains("atinge cota") || text.contains("transborda")))) {
            return Optional.of(NewsCategory.FLOOD);
        }

        // RAIN (chuva, tempestade, temporal, granizo, precipitação, CEMADEN)
        if (text.contains("chuva") || text.contains("temporal") || text.contains("tempestade") ||
            text.contains("granizo") || text.contains("tromba d'agua") || text.contains("precipitacao") ||
            text.contains("ciclone") || text.contains("vendaval") || text.contains("cemaden") ||
            text.contains("alerta meteorologico")) {
            return Optional.of(NewsCategory.RAIN);
        }

        // DEFORESTATION (desmatamento, desmatar, pressão agropecuária, DETER, conservação florestal)
        if (text.contains("desmatamento") || text.contains("desmatar") || text.contains("corte raso") ||
            text.contains("deter") || text.contains("agropecuaria sobre floresta") ||
            text.contains("pressao agropecuaria") || text.contains("conservacao florestal") ||
            text.contains("taxa de desmatamento") || text.contains("perda de vegetacao")) {
            return Optional.of(NewsCategory.DEFORESTATION);
        }

        // GENERAL (seca, estiagem, calor extremo, ar seco, baixa umidade)
        if (text.contains("seca") || text.contains("estiagem") || text.contains("calor extremo") ||
            text.contains("onda de calor") || text.contains("ar seco") || text.contains("baixa umidade") ||
            text.contains("umidade relativa do ar") || text.contains("temperatura recorde") ||
            text.contains("crise hidrica") || text.contains("defesa civil")) {
            return Optional.of(NewsCategory.GENERAL);
        }

        return Optional.empty();
    }

    /**
     * Extrai município e estado a partir do título e texto da matéria.
     */
    public LocationInfo extractLocation(String title, String description) {
        String combinedOriginal = (title != null ? title : "") + " " + (description != null ? description : "");
        String textNormalized = stripAccents(combinedOriginal.toLowerCase());

        String foundCity = null;
        String foundState = null;

        // 1. Procura por capitais
        for (Map.Entry<String, String> entry : CAPITAL_TO_STATE.entrySet()) {
            String capital = entry.getKey();
            Pattern p = Pattern.compile("\\b" + Pattern.quote(capital) + "\\b");
            if (p.matcher(textNormalized).find()) {
                foundCity = capitalizeProper(capital);
                foundState = entry.getValue();
                break;
            }
        }

        // 2. Se achou cidade mas não estado, ou se ainda não achou estado, procura menções a Estados pelo nome
        if (foundState == null) {
            for (Map.Entry<String, String> entry : STATE_BY_NAME.entrySet()) {
                String stateName = entry.getKey();
                Pattern p = Pattern.compile("\\b" + Pattern.quote(stateName) + "\\b");
                if (p.matcher(textNormalized).find()) {
                    foundState = entry.getValue();
                    if (foundCity == null && (stateName.equals("sao paulo") || stateName.equals("rio de janeiro"))) {
                        foundCity = capitalizeProper(stateName);
                    }
                    break;
                }
            }
        }

        // 3. Procura siglas de UF no formato "(SP)", "- SP", "em SP", "no RS", "/DF"
        if (foundState == null) {
            for (String uf : UF_LIST) {
                Pattern ufPattern = Pattern.compile("[\\s\\(/–-]" + uf + "[\\s\\)\\.,;–-]");
                if (ufPattern.matcher(combinedOriginal).find()) {
                    foundState = uf;
                    break;
                }
            }
        }

        return new LocationInfo(foundCity, foundState);
    }

    /**
     * Sanitiza a descrição, removendo tags HTML, unescaping entidades e truncando em até 2 linhas de linguagem cidadã.
     */
    public String cleanAndSummarize(String rawHtml, int maxLength) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        // Remove scripts e styles
        String clean = rawHtml.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        // Remove tags HTML
        clean = clean.replaceAll("<[^>]+>", " ");
        // Decodifica entidades comuns
        clean = decodeHtmlEntities(clean);
        // Normaliza espaços em branco e quebras de linha
        clean = clean.replaceAll("\\s+", " ").trim();

        if (clean.length() <= maxLength) {
            return clean;
        }

        // Corta de forma elegante na última pontuação ou espaço
        String substring = clean.substring(0, maxLength);
        int lastSentence = Math.max(substring.lastIndexOf(". "), Math.max(substring.lastIndexOf("! "), substring.lastIndexOf("? ")));
        if (lastSentence > maxLength / 2) {
            return substring.substring(0, lastSentence + 1).trim();
        }

        int lastSpace = substring.lastIndexOf(' ');
        if (lastSpace > 0) {
            return substring.substring(0, lastSpace).trim() + "...";
        }

        return substring + "...";
    }

    private String decodeHtmlEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&atilde;", "ã")
                .replace("&otilde;", "õ")
                .replace("&aacute;", "á")
                .replace("&eacute;", "é")
                .replace("&iacute;", "í")
                .replace("&oacute;", "ó")
                .replace("&uacute;", "ú")
                .replace("&ccedil;", "ç");
    }

    public String stripAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String capitalizeProper(String s) {
        if (s == null || s.isBlank()) return "";
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.length() > 2 && !p.equals("dos") && !p.equals("das") && !p.equals("do") && !p.equals("da") && !p.equals("de")) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            } else {
                sb.append(p).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
