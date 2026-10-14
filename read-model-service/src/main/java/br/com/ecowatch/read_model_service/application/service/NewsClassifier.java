package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classificador temático e extrator de localização e resumos de notícias ambientais.
 */
@Component
public class NewsClassifier {

    public record LocationInfo(String municipio, String estado) {}

    private static final Map<String, String> STATE_BY_NAME = new LinkedHashMap<>();
    private static final Map<String, String> CAPITAL_TO_STATE = new LinkedHashMap<>();

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
     * Classifica a notícia com base no título e descrição.
     * Retorna Optional.empty() se não for categorizada como desastre/clima.
     */
    public Optional<NewsCategory> classify(String title, String description, boolean isEnvironmentalFeed) {
        String text = stripAccents((title + " " + (description != null ? description : "")).toLowerCase());

        // 1. DENGUE
        if (text.contains("dengue") || text.contains("aedes") || text.contains("chikungunya") ||
            text.contains("zika") || text.contains("fumace") || text.contains("arbovirose")) {
            return Optional.of(NewsCategory.DENGUE);
        }

        // 2. FIRE (queimadas, incêndios)
        if (text.contains("queimada") || text.contains("incendio") || text.contains("foco de calor") ||
            text.contains("fumaca") || text.contains("brigadista") || text.contains("pantanal em chamas") ||
            text.contains("amazonia em chamas") || text.contains("combate ao fogo") || text.contains("inpe")) {
            return Optional.of(NewsCategory.FIRE);
        }

        // 3. FLOOD (enchentes, inundações, deslizamentos)
        if (text.contains("enchente") || text.contains("inundacao") || text.contains("alagamento") ||
            text.contains("deslizamento") || text.contains("transbord") || text.contains("enxurrada") ||
            text.contains("cota de inundacao") || text.contains("cota de alerta") || text.contains("desabrigado") ||
            text.contains("desabamento") || text.contains("dique") || text.contains("represa transborda")) {
            return Optional.of(NewsCategory.FLOOD);
        }

        // 4. RAIN (chuvas fortes, tempestades, granizo)
        if (text.contains("chuva") || text.contains("temporal") || text.contains("tempestade") ||
            text.contains("granizo") || text.contains("tromba d'agua") || text.contains("precipitacao") ||
            text.contains("ciclone") || text.contains("vendaval") || text.contains("alerta meteorologico") ||
            text.contains("inmet")) {
            return Optional.of(NewsCategory.RAIN);
        }

        // 5. GENERAL (secas, estiagens, calor extremo, defesa civil)
        if (text.contains("seca") || text.contains("estiagem") || text.contains("onda de calor") ||
            text.contains("calor extremo") || text.contains("temperatura recorde") || text.contains("crise hidrica") ||
            text.contains("defesa civil") || text.contains("cemaden") || text.contains("desastre natural") ||
            text.contains("emergencia climatica") || text.contains("aquecimento global")) {
            return Optional.of(NewsCategory.GENERAL);
        }

        // Se o feed for temático ambiental e o texto trata de clima/natureza/biomas
        if (isEnvironmentalFeed && (text.contains("clima") || text.contains("meio ambiente") ||
                text.contains("floresta") || text.contains("amazonia") || text.contains("pantanal") ||
                text.contains("cerrado") || text.contains("mata atlantica") || text.contains("ibama"))) {
            return Optional.of(NewsCategory.GENERAL);
        }

        return Optional.empty();
    }

    /**
     * Extrai município e estado a partir do título e texto da matéria.
     */
    public LocationInfo extractLocation(String title, String description) {
        String combinedOriginal = title + " " + (description != null ? description : "");
        String textNormalized = stripAccents(combinedOriginal.toLowerCase());

        String foundCity = null;
        String foundState = null;

        // 1. Procura por capitais
        for (Map.Entry<String, String> entry : CAPITAL_TO_STATE.entrySet()) {
            String capital = entry.getKey();
            Pattern p = Pattern.compile("\\b" + capital + "\\b");
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
                Pattern p = Pattern.compile("\\b" + stateName + "\\b");
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

    private String stripAccents(String s) {
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
