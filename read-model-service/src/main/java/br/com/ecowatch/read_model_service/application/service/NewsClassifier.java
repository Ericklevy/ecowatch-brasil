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

    // Termos de rejeição obrigatória (Blacklist de Ruído Político, Economia Geral e Trivialidades)
    private static final List<String> BLACKLIST_TERMS = List.of(
            "urna", "urnas", "eleicao", "eleicoes", "2o turno", "2º turno", "segundo turno",
            "candidato", "candidatos", "candidata", "partido", "partidos", "stf", "voto", "votos",
            "votacao", "tse", "parlamentar", "deputado", "deputados", "senador", "senadores",
            "plenario", "tribunal superior", "marco temporal", "lixo eletronico", "florada do ipe",
            "tomando um solzinho", "tomando solzinho", "ninhos nunca vistos", "zoologico", "parque das aves",
            "filhote de", "receita federal", "imposto de renda", "inflacao", "selic", "copom",
            "bolsa familia", "futebol", "campeonato", "copa do brasil", "libertadores", "brasileirao",
            "trump", "biden", "bolsa de valores", "mercado financeiro", "industria", "empresario",
            "escritor", "livro", "premio", "cinema", "teatro", "show", "cultura", "novela", "serie", "filme"
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
        // Nota: "para" não entra direto aqui para não colidir com a preposição "para". É tratado separadamente.
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

    private boolean containsWord(String text, String word) {
        if (text == null || word == null) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    /**
     * Verifica se o texto contém algum termo proibido da Blacklist.
     */
    public boolean isBlacklisted(String text) {
        if (text == null || text.isBlank()) return true;
        for (String term : BLACKLIST_TERMS) {
            if (term.contains(" ")) {
                if (text.contains(term)) return true;
            } else {
                if (containsWord(text, term)) return true;
            }
        }
        return false;
    }

    /**
     * Verifica se o texto atende à exigência mínima de conter palavras do domínio de desastres/clima.
     * Palavras de uma letra ou curtas usam fronteira estrita de palavra para evitar falsos positivos
     * (ex: 'rio' não dá match em 'empresário', 'relatório' ou 'cenário').
     */
    public boolean hasDomainRelevance(String text) {
        if (text == null || text.isBlank()) return false;
        for (String term : DOMAIN_WHITELIST) {
            if (term.contains(" ")) {
                if (text.contains(term)) return true;
            } else {
                if (containsWord(text, term)) return true;
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

        // 2. Exigência de relevância de domínio (Whitelist estrita)
        if (!hasDomainRelevance(text)) {
            return Optional.empty();
        }

        // 3. Classificação Semântica Estrita

        // DENGUE
        if (containsWord(text, "dengue") || containsWord(text, "aedes") || containsWord(text, "chikungunya") ||
            containsWord(text, "zika") || containsWord(text, "fumace") || containsWord(text, "arbovirose") ||
            containsWord(text, "infodengue")) {
            return Optional.of(NewsCategory.DENGUE);
        }

        // FIRE (fogo, queimada, incêndio, fumaça, chamas, INPE associado a calor)
        if (containsWord(text, "fogo") || containsWord(text, "queimada") || containsWord(text, "queimadas") ||
            containsWord(text, "incendio") || containsWord(text, "incendios") || containsWord(text, "fumaca") ||
            containsWord(text, "chamas") || containsWord(text, "brigadista") || containsWord(text, "brigadistas") ||
            text.contains("foco de calor") || text.contains("focos de calor") || text.contains("pantanal em chamas") ||
            text.contains("amazonia em chamas") || text.contains("combate ao fogo")) {
            return Optional.of(NewsCategory.FIRE);
        }

        // FLOOD (rio, rios, enchente, cheia, inundação, vazante, cota, Guaíba, Rio Negro)
        boolean hasRiver = containsWord(text, "rio") || containsWord(text, "rios") ||
                text.contains("rio guaiba") || text.contains("rio negro") || text.contains("rio amazonas") ||
                text.contains("rio madeira") || text.contains("rio acre");
        boolean hasRiverCondition = containsWord(text, "cheia") || containsWord(text, "cheias") ||
                containsWord(text, "transborda") || containsWord(text, "transbordou") ||
                containsWord(text, "vazante") || containsWord(text, "cota") ||
                containsWord(text, "inundou") || containsWord(text, "subiu") ||
                containsWord(text, "sobe") || containsWord(text, "alagou");

        if (containsWord(text, "enchente") || containsWord(text, "enchentes") ||
            containsWord(text, "inundacao") || containsWord(text, "inundacoes") ||
            containsWord(text, "alagamento") || containsWord(text, "alagamentos") ||
            containsWord(text, "deslizamento") || containsWord(text, "deslizamentos") ||
            containsWord(text, "enxurrada") || containsWord(text, "enxurradas") ||
            text.contains("cota de inundacao") || text.contains("cota de alerta") ||
            text.contains("cheia do rio") || text.contains("cheia dos rios") ||
            (hasRiver && hasRiverCondition)) {
            return Optional.of(NewsCategory.FLOOD);
        }

        // RAIN (chuva, tempestade, temporal, granizo, precipitação, CEMADEN)
        if (containsWord(text, "chuva") || containsWord(text, "chuvas") ||
            containsWord(text, "temporal") || containsWord(text, "temporais") ||
            containsWord(text, "tempestade") || containsWord(text, "tempestades") ||
            containsWord(text, "granizo") || containsWord(text, "precipitacao") ||
            containsWord(text, "ciclone") || containsWord(text, "vendaval") ||
            containsWord(text, "cemaden") || text.contains("tromba d'agua") ||
            text.contains("alerta meteorologico")) {
            return Optional.of(NewsCategory.RAIN);
        }

        // DEFORESTATION (desmatamento, desmatar, corte raso, DETER, conservação florestal)
        if (containsWord(text, "desmatamento") || containsWord(text, "desmatar") ||
            containsWord(text, "desmatada") || containsWord(text, "desmatadas") ||
            containsWord(text, "deter") || text.contains("corte raso") ||
            text.contains("agropecuaria sobre floresta") || text.contains("pressao agropecuaria") ||
            text.contains("conservacao florestal") || text.contains("taxa de desmatamento") ||
            text.contains("perda de vegetacao")) {
            return Optional.of(NewsCategory.DEFORESTATION);
        }

        // GENERAL (seca, estiagem, calor extremo, ar seco, baixa umidade)
        if (containsWord(text, "seca") || containsWord(text, "secas") ||
            containsWord(text, "estiagem") || containsWord(text, "estiagens") ||
            containsWord(text, "defesa civil") || text.contains("calor extremo") ||
            text.contains("onda de calor") || text.contains("ar seco") ||
            text.contains("baixa umidade") || text.contains("umidade relativa do ar") ||
            text.contains("temperatura recorde") || text.contains("crise hidrica")) {
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

        // 2.1 Detecção específica para o Estado do Pará (evita colisão com a preposição "para")
        if (foundState == null) {
            if (Pattern.compile("\\b(estado do para|no para|do para|governo do para)\\b").matcher(textNormalized).find()
                || Pattern.compile("\\bPará\\b").matcher(combinedOriginal).find()) {
                foundState = "PA";
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
