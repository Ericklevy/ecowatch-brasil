package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.*;
import br.com.ecowatch.read_model_service.util.CityNameNormalizer;
import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import br.com.ecowatch.read_model_service.infrastructure.entity.NewsEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.NewsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRagService {

    private final JdbcTemplate jdbcTemplate;
    private final NewsRepository newsRepository;
    private final DashboardService dashboardService;
    private final EmissoesService emissoesService;
    private final br.com.ecowatch.read_model_service.infrastructure.config.RagDatabaseInitializer ragDatabaseInitializer;

    /** Quando o Gemini responde 429 (cota grátis acabou), evitamos novas chamadas até este horário. */
    private volatile Instant cotaEsgotadaAte = Instant.EPOCH;

    /** Período de espera depois de um 429 antes de tentar o Gemini de novo. */
    private static final Duration PAUSA_COTA = Duration.ofMinutes(30);

    static final String AVISO_LIMITE = "⚠️ **O limite gratuito da IA foi atingido por agora.** "
            + "Enquanto isso, mostro abaixo os dados que o EcoWatch já tem salvos, sem a síntese da IA. "
            + "Tente de novo mais tarde para receber uma resposta completa.\n\n";

    /** As 27 capitais, com o nome como o usuário escreve. A chave é o nome normalizado (sem acento, minúsculo). */
    static final Map<String, String> CAPITAIS = new LinkedHashMap<>();
    static {
        for (String c : List.of("Rio Branco", "Maceió", "Macapá", "Manaus", "Salvador", "Fortaleza", "Brasília",
                "Vitória", "Goiânia", "São Luís", "Cuiabá", "Campo Grande", "Belo Horizonte", "Belém",
                "João Pessoa", "Curitiba", "Recife", "Teresina", "Rio de Janeiro", "Natal", "Porto Alegre",
                "Porto Velho", "Boa Vista", "Florianópolis", "São Paulo", "Aracaju", "Palmas")) {
            CAPITAIS.put(CityNameNormalizer.normalize(c), c);
        }
    }

    /** Mapeamento de cada capital para a respectiva Unidade Federativa (UF). */
    static final Map<String, String> CAPITAL_UF = Map.ofEntries(
            Map.entry("Rio Branco", "AC"), Map.entry("Maceió", "AL"), Map.entry("Macapá", "AP"),
            Map.entry("Manaus", "AM"), Map.entry("Salvador", "BA"), Map.entry("Fortaleza", "CE"),
            Map.entry("Brasília", "DF"), Map.entry("Vitória", "ES"), Map.entry("Goiânia", "GO"),
            Map.entry("São Luís", "MA"), Map.entry("Cuiabá", "MT"), Map.entry("Campo Grande", "MS"),
            Map.entry("Belo Horizonte", "MG"), Map.entry("Belém", "PA"), Map.entry("João Pessoa", "PB"),
            Map.entry("Curitiba", "PR"), Map.entry("Recife", "PE"), Map.entry("Teresina", "PI"),
            Map.entry("Rio de Janeiro", "RJ"), Map.entry("Natal", "RN"), Map.entry("Porto Alegre", "RS"),
            Map.entry("Porto Velho", "RO"), Map.entry("Boa Vista", "RR"), Map.entry("Florianópolis", "SC"),
            Map.entry("São Paulo", "SP"), Map.entry("Aracaju", "SE"), Map.entry("Palmas", "TO")
    );

    static final Map<String, String> ESTADOS_MAP = new LinkedHashMap<>();
    static {
        Map<String, String> m = Map.ofEntries(
                Map.entry("acre", "AC"), Map.entry("alagoas", "AL"), Map.entry("amapa", "AP"),
                Map.entry("amazonas", "AM"), Map.entry("bahia", "BA"), Map.entry("ceara", "CE"),
                Map.entry("distrito federal", "DF"), Map.entry("espirito santo", "ES"), Map.entry("goias", "GO"),
                Map.entry("maranhao", "MA"), Map.entry("mato grosso do sul", "MS"), Map.entry("mato grosso", "MT"),
                Map.entry("minas gerais", "MG"), Map.entry("para", "PA"), Map.entry("paraiba", "PB"),
                Map.entry("parana", "PR"), Map.entry("pernambuco", "PE"), Map.entry("piaui", "PI"),
                Map.entry("rio de janeiro", "RJ"), Map.entry("rio grande do norte", "RN"), Map.entry("rio grande do sul", "RS"),
                Map.entry("rondonia", "RO"), Map.entry("roraima", "RR"), Map.entry("santa catarina", "SC"),
                Map.entry("sao paulo", "SP"), Map.entry("sergipe", "SE"), Map.entry("tocantins", "TO")
        );
        for (var e : m.entrySet()) {
            ESTADOS_MAP.put(CityNameNormalizer.normalize(e.getKey()), e.getValue());
        }
    }

    public static boolean isEmissoesQuery(String query) {
        if (query == null) return false;
        String q = CityNameNormalizer.normalize(query).toLowerCase();
        return q.contains("emissao") || q.contains("emissoes") || q.contains("emite")
                || q.contains("metano") || q.contains("ch4")
                || q.contains("co2") || q.contains("carbono")
                || q.contains("efeito estufa") || q.contains("gases")
                || q.contains("gas de efeito") || q.contains("seeg")
                || q.contains("climatetrace") || q.contains("climate trace");
    }

    public static String detectarGas(String query) {
        if (query == null) return "CO2e";
        String q = CityNameNormalizer.normalize(query).toLowerCase();
        if (q.contains("metano") || q.contains("ch4")) return "CH4";
        if (q.contains("co2") || q.contains("carbono")) return "CO2e";
        return "CO2e";
    }

    public static String detectarEstado(String texto, String estadoRequest) {
        if (estadoRequest != null && !estadoRequest.isBlank() && !"ALL".equalsIgnoreCase(estadoRequest)) {
            return estadoRequest.trim().toUpperCase();
        }
        if (texto == null) return null;
        String norm = " " + CityNameNormalizer.normalize(texto).replaceAll("[^a-z0-9 ]", " ") + " ";
        for (var e : ESTADOS_MAP.entrySet()) {
            if (norm.contains(" " + e.getKey() + " ")) {
                return e.getValue();
            }
        }
        for (String uf : CAPITAL_UF.values()) {
            if (norm.contains(" " + uf.toLowerCase() + " ")) {
                return uf;
            }
        }
        return null;
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${gemini.api.model:gemini-flash-lite-latest}")
    private String geminiModel;

    @Value("${gemini.api.embedding-model:gemini-embedding-001}")
    private String embeddingModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    /**
     * Responde a uma pergunta do usuário combinando busca vetorial nas notícias com IA generativa (RAG).
     */
    public AiAskResponse ask(AiAskRequest request) {
        ragDatabaseInitializer.ensureInitialized();
        long startTime = System.currentTimeMillis();
        String pergunta = request.getPergunta().trim();
        int topK = request.getTopK() != null && request.getTopK() > 0 ? Math.min(request.getTopK(), 8) : 4;

        log.info("Recebida pergunta RAG: '{}' (Cat: {}, UF: {}, TopK: {})",
                pergunta, request.getCategoria(), request.getEstado(), topK);

        // 1. Dados medidos pelo próprio EcoWatch para as capitais citadas (na pergunta ou na conversa)
        List<String> cidades = detectarCapitais(pergunta, request.getHistorico());
        Map<String, DashboardDTO> dados = new LinkedHashMap<>();
        for (String cidade : cidades) {
            try {
                DashboardDTO d = dashboardService.getDashboard(cidade);
                if (d != null && (!vazio(d.getAlertas()) || !vazio(d.getPrevisoes()))) {
                    dados.put(cidade, d);
                }
            } catch (Exception e) {
                log.warn("Não consegui ler os dados de {}: {}", cidade, e.getMessage());
            }
        }

        // 2. Inferência de estado: verifica se citou estado por extenso/sigla ou deduz da capital
        String estadoEfetivo = detectarEstado(pergunta, request.getEstado());
        if ((estadoEfetivo == null || estadoEfetivo.isBlank() || "all".equalsIgnoreCase(estadoEfetivo)) && !cidades.isEmpty()) {
            estadoEfetivo = CAPITAL_UF.get(cidades.get(0));
        }

        // 3. Contexto específico de Gases de Efeito Estufa e Emissões (SEEG, Climate TRACE, CAMS)
        String contextoEmissoes = montarContextoEmissoes(pergunta, cidades, estadoEfetivo);

        // 4. Embedding da pergunta (se o Gemini estiver disponível e com cota)
        String queryVectorStr = podeUsarGemini() ? generateEmbedding(pergunta) : null;

        // 5. Notícias parecidas com corte estrito de similaridade, filtro por estado e decaimento temporal
        List<RagSourceItem> fontes = searchSimilarNews(pergunta, queryVectorStr, request.getCategoria(), estadoEfetivo, topK);

        // 6. Resposta
        String respostaFinal;
        String modeloUtilizado;
        boolean limiteAtingido = false;

        if (fontes.isEmpty() && dados.isEmpty() && contextoEmissoes.isBlank()) {
            respostaFinal = "Não encontrei notícias nem dados medidos no EcoWatch que respondam a essa pergunta. "
                    + "Tente citar uma capital (ex.: *Como está o risco de incêndio em Cuiabá?*) ou usar outros termos.";
            modeloUtilizado = "ecowatch-rag-empty";
        } else if (podeUsarGemini()) {
            String llm = generateLlmAnswer(pergunta, fontes, dados, contextoEmissoes, request.getHistorico());
            if (llm != null) {
                respostaFinal = llm;
                modeloUtilizado = geminiModel;
            } else {
                limiteAtingido = cotaEsgotada();
                respostaFinal = (limiteAtingido ? AVISO_LIMITE : "") + generateFactualSummary(fontes, dados, contextoEmissoes);
                modeloUtilizado = "ecowatch-rag-synthesizer";
            }
        } else {
            limiteAtingido = cotaEsgotada();
            respostaFinal = (limiteAtingido ? AVISO_LIMITE : "") + generateFactualSummary(fontes, dados, contextoEmissoes);
            modeloUtilizado = "ecowatch-rag-synthesizer";
        }

        respostaFinal = sanitizeLatex(respostaFinal);

        long elapsedMs = System.currentTimeMillis() - startTime;

        return AiAskResponse.builder()
                .pergunta(pergunta)
                .resposta(respostaFinal)
                .fontes(fontes)
                .totalFontesEncontradas(fontes.size())
                .modelo(modeloUtilizado)
                .tempoRespostaMs(elapsedMs)
                .limiteAtingido(limiteAtingido)
                .cidadesConsultadas(new ArrayList<>(dados.keySet()))
                .periodoFontes(periodo(fontes))
                .build();
    }

    /** Capitais citadas na pergunta; se nenhuma, as da última pergunta do usuário (para "e lá, vai chover?"). */
    static List<String> detectarCapitais(String pergunta, List<AiAskRequest.HistoricoItem> historico) {
        List<String> achadas = capitaisNoTexto(pergunta);
        if (achadas.isEmpty() && historico != null) {
            for (int i = historico.size() - 1; i >= 0 && achadas.isEmpty(); i--) {
                AiAskRequest.HistoricoItem h = historico.get(i);
                if (h != null && "usuario".equalsIgnoreCase(h.getAutor())) {
                    achadas = capitaisNoTexto(h.getTexto());
                }
            }
        }
        return achadas.size() > 3 ? achadas.subList(0, 3) : achadas;
    }

    private static List<String> capitaisNoTexto(String texto) {
        String t = " " + CityNameNormalizer.normalize(texto).replaceAll("[^a-z ]", " ") + " ";
        List<String> achadas = new ArrayList<>();
        for (var e : CAPITAIS.entrySet()) {
            if (t.contains(" " + e.getKey() + " ")) {
                achadas.add(e.getValue());
            }
        }
        return achadas;
    }

    private static boolean vazio(List<?> l) {
        return l == null || l.isEmpty();
    }

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.of("America/Sao_Paulo"));

    static String periodo(List<RagSourceItem> fontes) {
        List<Instant> datas = fontes.stream().map(RagSourceItem::getDataHora).filter(Objects::nonNull).sorted().toList();
        if (datas.isEmpty()) return null;
        String ini = DIA.format(datas.get(0)), fim = DIA.format(datas.get(datas.size() - 1));
        return ini.equals(fim) ? ini : ini + " a " + fim;
    }

    private boolean podeUsarGemini() {
        return isGeminiConfigured() && !cotaEsgotada();
    }

    boolean cotaEsgotada() {
        return Instant.now().isBefore(cotaEsgotadaAte);
    }

    private void registrarCotaEsgotada() {
        cotaEsgotadaAte = Instant.now().plus(PAUSA_COTA);
        log.warn("Cota do Gemini esgotada (429). Pausando chamadas por {} min.", PAUSA_COTA.toMinutes());
    }

    record GasesAtmosfericos(Double metano, Double co2, Double co) {}

    private final Map<String, GasesAtmosfericos> cacheGases = new java.util.concurrent.ConcurrentHashMap<>();

    private GasesAtmosfericos consultarGasesAtmosfericos(String capital) {
        PrevisaoRealService.Capital capCoord = PrevisaoRealService.CAPITAIS.stream()
                .filter(c -> c.municipio().equalsIgnoreCase(CityNameNormalizer.normalize(capital)))
                .findFirst().orElse(null);

        if (capCoord == null) return null;

        if (cacheGases.containsKey(capital)) {
            return cacheGases.get(capital);
        }

        try {
            String url = String.format(Locale.US,
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=methane,carbon_dioxide,carbon_monoxide",
                    capCoord.lat(), capCoord.lon());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode current = root.path("current");
                Double metano = current.has("methane") ? current.path("methane").asDouble() : null;
                Double co2 = current.has("carbon_dioxide") ? current.path("carbon_dioxide").asDouble() : null;
                Double co = current.has("carbon_monoxide") ? current.path("carbon_monoxide").asDouble() : null;

                GasesAtmosfericos gases = new GasesAtmosfericos(metano, co2, co);
                cacheGases.put(capital, gases);
                return gases;
            }
        } catch (Exception e) {
            log.debug("Aviso ao consultar gases atmosféricos para {}: {}", capital, e.getMessage());
        }
        return null;
    }

    public String montarContextoEmissoes(String pergunta, List<String> capitais, String ufAlvo) {
        if (!isEmissoesQuery(pergunta)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String gas = detectarGas(pergunta);
        String gasRotulo = "CH4".equalsIgnoreCase(gas) ? "Metano (CH₄)" : "CO₂ equivalente (CO₂e)";

        sb.append("=== DADOS OFICIAIS DE GASES E EMISSÕES (SEEG & CLIMATE TRACE) ===\n");
        sb.append("Gás foco da consulta: ").append(gasRotulo).append("\n\n");

        // 1. Capitais citadas: Concentração Atmosférica Atual (Open-Meteo) + Série SEEG (Fase 1 e 2)
        if (!capitais.isEmpty()) {
            for (String cap : capitais) {
                sb.append("--- CAPITAL: ").append(cap.toUpperCase()).append(" ---\n");

                // Concentração no ar hoje (CAMS/Open-Meteo)
                GasesAtmosfericos gases = consultarGasesAtmosfericos(cap);
                if (gases != null) {
                    sb.append("• Concentração no ar hoje (Fonte: Copernicus CAMS / Open-Meteo, leitura regional): ");
                    List<String> partes = new ArrayList<>();
                    if (gases.metano != null) partes.add(String.format(Locale.forLanguageTag("pt-BR"), "CH₄: %.0f µg/m³", gases.metano));
                    if (gases.co2 != null) partes.add(String.format(Locale.forLanguageTag("pt-BR"), "CO₂: %.0f ppm", gases.co2));
                    if (gases.co != null) partes.add(String.format(Locale.forLanguageTag("pt-BR"), "CO: %.0f µg/m³", gases.co));
                    sb.append(String.join(", ", partes)).append("\n");
                    sb.append("  (Nota: concentração no ar mede a dispersão atmosférica na região e não indica quem emite).\n");
                } else {
                    sb.append("• Concentração no ar hoje: sem dado em tempo real no momento (limite da API regional atingido).\n");
                }

                // Série SEEG do município
                try {
                    EmissoesDTO.MunicipioDetalheDTO det = emissoesService.getMunicipioDetalhe(cap, gas);
                    if (det != null && !det.getSerieHistorica().isEmpty()) {
                        String gasReal = (det.getGas() != null && !det.getGas().isBlank()) ? det.getGas() : gas;
                        sb.append(String.format("• Histórico de Emissões Municipais (Fonte: SEEG / Observatório do Clima, Coleção 14.0):\n"));
                        sb.append(String.format("  - Setor dominante de emissão em %s: %s (%s)\n", cap, det.getSetorDominante(), det.getExplicacaoSetorDominante()));
                        for (EmissoesDTO.SerieAnoDTO anoItem : det.getSerieHistorica()) {
                            sb.append(String.format(Locale.forLanguageTag("pt-BR"), "  - Ano %d: %,.1f t de %s", anoItem.getAno(), anoItem.getEmissaoTotal(), gasReal));
                            if (anoItem.getQuebraPorSetor() != null && !anoItem.getQuebraPorSetor().isEmpty()) {
                                String quebraStr = anoItem.getQuebraPorSetor().entrySet().stream()
                                        .map(s -> String.format(Locale.forLanguageTag("pt-BR"), "%s: %,.0f t", s.getKey(), s.getValue()))
                                        .collect(Collectors.joining("; "));
                                sb.append(" [").append(quebraStr).append("]");
                            }
                            sb.append("\n");
                        }
                    } else {
                        sb.append(String.format("• Histórico de Emissões Municipais (Fonte: SEEG 14.0): sem dado de %s para o município de %s.\n", gas, cap));
                    }
                } catch (Exception ex) {
                    log.debug("Erro ao carregar SEEG para {}: {}", cap, ex.getMessage());
                }
                sb.append("\n");
            }
        }

        // 2. Ranking do Estado (SEEG Top 5) se estado citado ou capital detectada
        String estadoConsulta = ufAlvo;
        if ((estadoConsulta == null || estadoConsulta.isBlank()) && !capitais.isEmpty()) {
            estadoConsulta = CAPITAL_UF.get(capitais.get(0));
        }

        if (estadoConsulta != null && !estadoConsulta.isBlank()) {
            sb.append(String.format("--- TOP 5 MUNICÍPIOS QUE MAIS EMITEM EM %s (Fonte: SEEG 14.0, Ano recente) ---\n", estadoConsulta));
            try {
                List<EmissoesDTO.RankingMunicipioItem> ranking = emissoesService.getRanking(gas, estadoConsulta, null, 5);
                if (!ranking.isEmpty()) {
                    for (int i = 0; i < ranking.size(); i++) {
                        EmissoesDTO.RankingMunicipioItem item = ranking.get(i);
                        sb.append(String.format(Locale.forLanguageTag("pt-BR"),
                                "%d. %s (%s): %,.1f t de %s/ano | Setor principal: %s (%.1f%%) | %s\n",
                                i + 1, item.getMunicipio(), item.getUf(), item.getEmissaoTotal(), item.getGas(),
                                item.getSetorPrincipal(), item.getPercentualSetorPrincipal(), item.getExplicacaoSetor()));
                    }
                } else {
                    sb.append("Sem dados de ranking para este estado.\n");
                }
            } catch (Exception ex) {
                log.debug("Erro ao carregar ranking de estado {}: {}", estadoConsulta, ex.getMessage());
            }
            sb.append("\n");

            // 3. Top 5 Fontes Pontuais do Climate TRACE no estado
            sb.append(String.format("--- TOP FONTES PONTUAIS EM %s (Fonte: Climate TRACE, CC BY 4.0) ---\n", estadoConsulta));
            try {
                List<EmissoesDTO.FonteEmissaoDTO> fontesTrace = emissoesService.getFontesEmissaoPorEstado(gas, estadoConsulta, 5);
                if (!fontesTrace.isEmpty()) {
                    for (int i = 0; i < fontesTrace.size(); i++) {
                        EmissoesDTO.FonteEmissaoDTO f = fontesTrace.get(i);
                        sb.append(String.format(Locale.forLanguageTag("pt-BR"),
                                "%d. %s [%s - %s]: %,.1f t/ano (Ano %d)\n",
                                i + 1, f.getNome(), f.getSetorRotulo(), f.getSubSetor() != null ? f.getSubSetor() : "instalação",
                                f.getEmissaoAnualT(), f.getAno()));
                    }
                } else {
                    sb.append(String.format("Nenhuma grande fonte pontual do top nacional do Climate TRACE localizada diretamente em %s.\n", estadoConsulta));
                }
            } catch (Exception ex) {
                log.debug("Erro ao carregar fontes Climate TRACE: {}", ex.getMessage());
            }
            sb.append("\n");
        } else {
            // Se nenhum estado ou capital foi citado: inclui ranking nacional dos Top Estados e Top Municípios do Brasil
            String qNorm = CityNameNormalizer.normalize(pergunta).toLowerCase();
            boolean querMetano = qNorm.contains("metano") || qNorm.contains("ch4");
            boolean querCo2 = qNorm.contains("co2") || qNorm.contains("carbono");

            List<String> gasesNacionais = new ArrayList<>();
            if (querMetano) gasesNacionais.add("CH4");
            if (querCo2) gasesNacionais.add("CO2e");
            if (gasesNacionais.isEmpty()) gasesNacionais.add(gas);

            for (String g : gasesNacionais) {
                String gRotulo = "CH4".equalsIgnoreCase(g) ? "Metano (CH₄)" : "CO₂ equivalente (CO₂e)";
                sb.append(String.format("--- TOP 5 ESTADOS QUE MAIS EMITEM %s NO BRASIL (Fonte: SEEG 14.0, Ano 2023) ---\n", gRotulo));
                try {
                    List<EmissoesDTO.EmissaoEstadoItem> estados = emissoesService.getEstados(g, 2023);
                    if (estados != null && !estados.isEmpty()) {
                        int limiteEstados = Math.min(5, estados.size());
                        for (int i = 0; i < limiteEstados; i++) {
                            EmissoesDTO.EmissaoEstadoItem est = estados.get(i);
                            sb.append(String.format(Locale.forLanguageTag("pt-BR"),
                                    "%d. %s: %,.1f t de %s/ano | Setor principal: %s (%.1f%%) | %s\n",
                                    i + 1, est.getUf(), est.getEmissaoTotal(), g,
                                    est.getSetorPrincipal(), est.getPercentualSetorPrincipal(), est.getExplicacaoSetor()));
                        }
                    } else {
                        sb.append("Sem dados estaduais disponíveis.\n");
                    }
                } catch (Exception ex) {
                    log.debug("Erro ao carregar top estados {}: {}", g, ex.getMessage());
                }
                sb.append("\n");

                sb.append(String.format("--- TOP 5 MUNICÍPIOS QUE MAIS EMITEM %s NO BRASIL (Fonte: SEEG 14.0, Ano 2023) ---\n", gRotulo));
                try {
                    List<EmissoesDTO.RankingMunicipioItem> ranking = emissoesService.getRanking(g, null, 2023, 5);
                    if (ranking != null && !ranking.isEmpty()) {
                        for (int i = 0; i < ranking.size(); i++) {
                            EmissoesDTO.RankingMunicipioItem item = ranking.get(i);
                            sb.append(String.format(Locale.forLanguageTag("pt-BR"),
                                    "%d. %s (%s): %,.1f t de %s/ano | Setor principal: %s (%.1f%%) | %s\n",
                                    i + 1, item.getMunicipio(), item.getUf(), item.getEmissaoTotal(), item.getGas(),
                                    item.getSetorPrincipal(), item.getPercentualSetorPrincipal(), item.getExplicacaoSetor()));
                        }
                    } else {
                        sb.append("Sem dados de ranking municipal disponíveis.\n");
                    }
                } catch (Exception ex) {
                    log.debug("Erro ao carregar ranking nacional de municípios {}: {}", g, ex.getMessage());
                }
                sb.append("\n");

                sb.append(String.format("--- TOP FONTES PONTUAIS DO BRASIL DE %s (Fonte: Climate TRACE, CC BY 4.0) ---\n", gRotulo));
                try {
                    List<EmissoesDTO.FonteEmissaoDTO> fontesTrace = emissoesService.getFontesEmissao(g, null, 5);
                    if (fontesTrace != null && !fontesTrace.isEmpty()) {
                        for (int i = 0; i < fontesTrace.size(); i++) {
                            EmissoesDTO.FonteEmissaoDTO f = fontesTrace.get(i);
                            sb.append(String.format(Locale.forLanguageTag("pt-BR"),
                                    "%d. %s [%s - %s]: %,.1f t/ano (Ano %d)\n",
                                    i + 1, f.getNome(), f.getSetorRotulo(), f.getSubSetor() != null ? f.getSubSetor() : "instalação",
                                    f.getEmissaoAnualT(), f.getAno()));
                        }
                    } else {
                        sb.append("Nenhuma grande fonte pontual encontrada.\n");
                    }
                } catch (Exception ex) {
                    log.debug("Erro ao carregar fontes pontuais nacionais Climate TRACE: {}", ex.getMessage());
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String contextoDados(Map<String, DashboardDTO> dados) {
        StringBuilder sb = new StringBuilder();
        LocalDateTime limite = LocalDateTime.now().minusDays(3);
        for (var e : dados.entrySet()) {
            sb.append("--- ").append(e.getKey().toUpperCase()).append(" ---\n");
            var alertas = e.getValue().getAlertas() == null ? List.<AlertDTO>of() : e.getValue().getAlertas().stream()
                    .filter(a -> a.getDataHoraAlerta() == null || a.getDataHoraAlerta().isAfter(limite)).limit(5).toList();
            if (alertas.isEmpty()) {
                sb.append("Alertas nos últimos 3 dias: nenhum.\n");
            } else {
                sb.append("Alertas nos últimos 3 dias:\n");
                for (AlertDTO a : alertas) {
                    sb.append(String.format("• %s (%s): %s%n", a.getTipoAlerta(),
                            a.getDataHoraAlerta() != null ? DATE_FORMATTER.format(a.getDataHoraAlerta().atZone(ZoneId.of("America/Sao_Paulo"))) : "sem data",
                            a.getMensagem()));
                }
            }
            if (!vazio(e.getValue().getPrevisoes())) {
                sb.append("Previsão:\n");
                for (PrevisaoDTO pr : e.getValue().getPrevisoes()) {
                    sb.append(String.format("• %s: %s °C, chuva %s mm, umidade %s%%, risco de incêndio %s/100, risco de dengue %s/100%n",
                            pr.getDataPrevisao(), num(pr.getTemperaturaPrevista()), num(pr.getPrecipitacaoPrevista()),
                            num(pr.getUmidadePrevista()), pr.getRiscoIncendio(), pr.getRiscoDengue()));
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String num(Double v) {
        return v == null ? "?" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f", v);
    }

    /**
     * Busca as notícias mais relevantes no banco de dados com corte de similaridade e relevância geográfica/temporal.
     */
    private List<RagSourceItem> searchSimilarNews(String queryText, String vectorStr, NewsCategory categoria, String estado, int limit) {
        // Se temos vetor, realiza busca de vizinhos no pgvector com pontuação calibrada
        if (vectorStr != null) {
            try {
                String sql = """
                    SELECT r.noticia_id, n.titulo, n.resumo, n.fonte, n.url, n.data_hora, n.categoria, n.municipio, n.estado,
                           (1 - (r.embedding <=> ?::vector)) as similaridade
                    FROM rag_noticias_embeddings r
                    JOIN noticias_ambientais n ON r.noticia_id = n.id
                    WHERE (? IS NULL OR n.categoria = ?)
                    ORDER BY r.embedding <=> ?::vector ASC
                    LIMIT 12
                """;

                String catStr = categoria != null ? categoria.name() : null;
                String ufAlvo = estado != null && !estado.isBlank() ? estado.trim().toUpperCase() : null;

                List<RagSourceItem> candidates = jdbcTemplate.query(
                        sql,
                        ps -> {
                            ps.setString(1, vectorStr);
                            ps.setString(2, catStr);
                            ps.setString(3, catStr);
                            ps.setString(4, vectorStr);
                        },
                        (rs, rowNum) -> {
                            Instant dataHora = rs.getTimestamp("data_hora") != null ? rs.getTimestamp("data_hora").toInstant() : null;
                            double rawSim = rs.getDouble("similaridade");
                            String itemUf = rs.getString("estado");

                            // Decaimento temporal: notícias antigas têm leve penalização para priorizar fatos recentes
                            double scoreAjustado = rawSim;
                            if (dataHora != null) {
                                long diasAtras = Duration.between(dataHora, Instant.now()).toDays();
                                if (diasAtras > 40) {
                                    scoreAjustado -= 0.08;
                                } else if (diasAtras > 20) {
                                    scoreAjustado -= 0.04;
                                } else if (diasAtras <= 7) {
                                    scoreAjustado += 0.02;
                                }
                            }

                            // Corte de similaridade mínima:
                            // Se a consulta possui estado específico, notícias de outros estados exigem similaridade estrita (>= 0.78)
                            // Notícias do mesmo estado ou gerais exigem similaridade mínima de 0.70 / 0.72
                            double corteMinimo;
                            if (ufAlvo != null) {
                                if (itemUf != null && itemUf.equalsIgnoreCase(ufAlvo)) {
                                    corteMinimo = 0.70;
                                } else if (itemUf == null || itemUf.isBlank()) {
                                    corteMinimo = 0.72; // Notícia de abrangência nacional
                                } else {
                                    corteMinimo = 0.78; // Notícia de outro estado
                                }
                            } else {
                                corteMinimo = 0.72;
                            }

                            if (scoreAjustado < corteMinimo) {
                                return null;
                            }

                            return RagSourceItem.builder()
                                    .id(rs.getLong("noticia_id"))
                                    .titulo(rs.getString("titulo"))
                                    .resumo(rs.getString("resumo"))
                                    .fonte(rs.getString("fonte"))
                                    .url(rs.getString("url"))
                                    .dataHora(dataHora)
                                    .categoria(NewsCategory.valueOf(rs.getString("categoria")))
                                    .municipio(rs.getString("municipio"))
                                    .estado(itemUf)
                                    .similaridade(Math.round(scoreAjustado * 100.0) / 100.0)
                                    .build();
                        }
                );

                List<RagSourceItem> itensValidos = candidates.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingDouble(RagSourceItem::getSimilaridade).reversed())
                        .limit(limit)
                        .toList();

                return itensValidos;
            } catch (Exception e) {
                log.warn("Busca vetorial via pgvector falhou: {}. Usando fallback contextual...", e.getMessage());
            }
        }

        // Fallback: Busca semântica por termos e relevância textual na tabela noticias_ambientais
        return searchFallbackByKeywords(queryText, categoria, estado, limit);
    }

    /**
     * Fallback relacional usando palavras-chave e ponderação temporal.
     */
    private List<RagSourceItem> searchFallbackByKeywords(String query, NewsCategory categoria, String estado, int limit) {
        try {
            // Extrai palavras significativas da pergunta (ignora stopwords comuns)
            List<String> keywords = Arrays.stream(query.toLowerCase().split("[\\s,;:.!?]+"))
                    .filter(w -> w.length() > 3 && !List.of("qual", "quais", "como", "onde", "quando", "para", "sobre", "esta", "estao", "noticia", "noticias").contains(w))
                    .toList();

            String likeTerm = "%" + (keywords.isEmpty() ? query : keywords.get(0)) + "%";
            String catStr = categoria != null ? categoria.name() : null;
            String ufStr = estado != null && !estado.isBlank() ? estado.trim().toUpperCase() : null;

            String sql = """
                SELECT id, titulo, resumo, fonte, url, data_hora, categoria, municipio, estado
                FROM noticias_ambientais
                WHERE (LOWER(titulo) LIKE LOWER(?) OR LOWER(resumo) LIKE LOWER(?))
                  AND (? IS NULL OR categoria = ?)
                  AND (? IS NULL OR estado = ? OR estado IS NULL)
                ORDER BY data_hora DESC
                LIMIT ?
            """;

            return jdbcTemplate.query(
                    sql,
                    ps -> {
                        ps.setString(1, likeTerm);
                        ps.setString(2, likeTerm);
                        ps.setString(3, catStr);
                        ps.setString(4, catStr);
                        ps.setString(5, ufStr);
                        ps.setString(6, ufStr);
                        ps.setInt(7, limit);
                    },
                    (rs, rowNum) -> RagSourceItem.builder()
                            .id(rs.getLong("id"))
                            .titulo(rs.getString("titulo"))
                            .resumo(rs.getString("resumo"))
                            .fonte(rs.getString("fonte"))
                            .url(rs.getString("url"))
                            .dataHora(rs.getTimestamp("data_hora").toInstant())
                            .categoria(NewsCategory.valueOf(rs.getString("categoria")))
                            .municipio(rs.getString("municipio"))
                            .estado(rs.getString("estado"))
                            .similaridade(0.85)
                            .build()
            );
        } catch (Exception e) {
            log.error("Erro no fallback de busca de notícias: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Envia o contexto das notícias recuperadas para o modelo Gemini formular a resposta final.
     */
    private String generateLlmAnswer(String pergunta, List<RagSourceItem> fontes, Map<String, DashboardDTO> dados,
                                     String contextoEmissoes, List<AiAskRequest.HistoricoItem> historico) {
        try {
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < fontes.size(); i++) {
                RagSourceItem f = fontes.get(i);
                contextBuilder.append(String.format(
                        "--- MATÉRIA %d ---\n" +
                        "Título: %s\n" +
                        "Fonte: %s\n" +
                        "Data: %s\n" +
                        "Categoria: %s\n" +
                        "Localidade: %s\n" +
                        "Resumo: %s\n" +
                        "Link Oficial: %s\n\n",
                        i + 1,
                        f.getTitulo(),
                        f.getFonte(),
                        DATE_FORMATTER.format(f.getDataHora()),
                        f.getCategoria(),
                        (f.getMunicipio() != null ? f.getMunicipio() + "/" : "") + (f.getEstado() != null ? f.getEstado() : "Brasil"),
                        f.getResumo(),
                        f.getUrl()
                ));
            }

            StringBuilder conversa = new StringBuilder();
            if (historico != null) {
                historico.stream().skip(Math.max(0, historico.size() - 4)).forEach(h -> {
                    if (h != null && h.getTexto() != null) {
                        String t = h.getTexto().length() > 500 ? h.getTexto().substring(0, 500) + "…" : h.getTexto();
                        conversa.append("usuario".equalsIgnoreCase(h.getAutor()) ? "Usuário: " : "EcoWatch AI: ").append(t).append("\n");
                    }
                });
            }

            String prompt = String.format("""
                Você é o EcoWatch AI, assistente de monitoramento climático e de desastres ambientais no Brasil.

                REGRAS GERAIS:
                1. Responda em português do Brasil, de forma clara e direta (no máximo uns 3 parágrafos curtos ou uma lista).
                2. Use SOMENTE as informações do CONTEXTO abaixo. Se o contexto não responde, diga isso com honestidade.
                3. Quando usar DADOS MEDIDOS, diga que são do monitoramento do EcoWatch e informe a data.
                4. Quando usar NOTÍCIAS, cite a fonte e a data (ex.: "segundo a Defesa Civil, em 20/11").
                5. Em situação de risco, oriente a seguir a Defesa Civil local (telefone 199).
                6. Pode usar **negrito** e listas com "- ".
                7. FORMATAÇÃO E FÓRMULAS QUÍMICAS: O site NÃO interpreta código LaTeX nem matemática Markdown. NUNCA use cifrões ($ ou $$), \\text{...} ou notação matemática LaTeX. Escreva sempre as fórmulas usando os números pequenos normais de texto: "CH₄" e "CO₂" (e também "N₂O", "CO₂e"). Escreva unidades sempre em texto puro (ex.: µg/m³, ppm, t/ano, toneladas).

                REGRAS OBRIGATÓRIAS PARA GASES DE EFEITO ESTUFA E EMISSÕES:
                - DIFERENCIAÇÃO FUNDAMENTAL:
                  * "Concentração atmosférica no ar" (modelo Copernicus CAMS / Open-Meteo, leitura recente, em µg/m³ ou ppm) mede a qualidade física do ar disperso e NÃO indica quem emite.
                  * "Emissões anuais" (SEEG por município ou Climate TRACE por instalação, em toneladas de CO₂e ou CH₄) indicam o quanto de gás é lançado na atmosfera pelo município ou fábrica.
                - NUNCA use LaTeX ($ ou \\text{}). Escreva fórmulas sempre em texto simples com subscritos: CH₄, CO₂, N₂O, CO₂e.
                - Se a pergunta for geral sobre o Brasil ou sobre quais estados/lugares mais emitem, apresente os rankings nacionais de estados fornecidos no contexto com seus respectivos setores dominantes e valores.
                - SEMPRE cite a fonte e o ano de cada dado (ex.: "Segundo o SEEG (2023)...", "De acordo com o Climate TRACE (2024)...", "Pelo modelo Copernicus CAMS hoje...").
                - NUNCA invente números de emissões ou concentrações. Se o dado não existir, diga com clareza.

                DADOS DE EMISSÕES E GASES (SEEG, CLIMATE TRACE E COPERNICUS CAMS):
                %s

                DADOS MEDIDOS PELO ECOWATCH (alertas e previsões das capitais citadas):
                %s

                NOTÍCIAS OFICIAIS:
                %s

                CONVERSA ANTERIOR:
                %s

                PERGUNTA:
                %s
                """, contextoEmissoes == null || contextoEmissoes.isBlank() ? "(nenhuma consulta de emissão/gases)" : contextoEmissoes,
                    dados.isEmpty() ? "(nenhuma capital citada)" : contextoDados(dados),
                    contextBuilder.isEmpty() ? "(nenhuma notícia relevante)" : contextBuilder,
                    conversa.isEmpty() ? "(início da conversa)" : conversa, pergunta);

            Map<String, Object> part = Map.of("text", prompt);
            Map<String, Object> content = Map.of("parts", List.of(part));
            Map<String, Object> generationConfig = Map.of(
                    "temperature", 0.2,
                    "maxOutputTokens", 800
            );
            Map<String, Object> requestPayload = Map.of(
                    "contents", List.of(content),
                    "generationConfig", generationConfig
            );

            String requestBody = objectMapper.writeValueAsString(requestPayload);
            String url = String.format(
                    "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                    geminiModel, geminiApiKey.trim()
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(httpResponse.body());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText().trim();
                    }
                }
            } else if (httpResponse.statusCode() == 429) {
                registrarCotaEsgotada();
            } else {
                log.warn("Gemini API retornou status {}: {}", httpResponse.statusCode(), httpResponse.body());
            }
        } catch (Exception e) {
            log.error("Erro ao chamar API do Gemini para gerar resposta: {}", e.getMessage());
        }

        // Se falhar, quem chamou monta a síntese com os dados salvos
        return null;
    }

    /**
     * Síntese factual inteligente sem alucinações para quando não há LLM externo ou a chave não foi definida.
     */
    private String generateFactualSummary(List<RagSourceItem> fontes, Map<String, DashboardDTO> dados, String contextoEmissoes) {
        StringBuilder sb = new StringBuilder();
        if (contextoEmissoes != null && !contextoEmissoes.isBlank()) {
            sb.append("**Monitoramento de Gases e Emissões:**\n\n")
              .append(contextoEmissoes.replace("--- ", "**").replace(" ---", "**\n"))
              .append("\n");
        }
        if (!dados.isEmpty()) {
            sb.append("**Dados medidos pelo EcoWatch:**\n\n").append(contextoDados(dados).replace("--- ", "**").replace(" ---", "**"));
        }
        if (fontes.isEmpty()) {
            return sb.toString().trim();
        }
        sb.append("Com base nas notícias oficiais monitoradas pelo EcoWatch Brasil, reunimos os seguintes dados apurados:\n\n");

        for (RagSourceItem f : fontes) {
            sb.append(String.format("📌 **%s**\n", f.getTitulo()));
            sb.append(String.format("• **Fonte:** %s | **Data:** %s", f.getFonte(), DATE_FORMATTER.format(f.getDataHora())));
            if (f.getEstado() != null) {
                sb.append(String.format(" | **Local:** %s", f.getMunicipio() != null ? f.getMunicipio() + "/" + f.getEstado() : f.getEstado()));
            }
            sb.append("\n");
            sb.append(String.format("• %s\n\n", f.getResumo()));
        }

        sb.append("Para consultar a íntegra, utilize os links oficiais referenciados nos cartões abaixo.");
        return sb.toString();
    }

    /**
     * Gera o vetor de embedding para um texto via API do Gemini.
     */
    public String generateEmbedding(String text) {
        if (!isGeminiConfigured() || text == null || text.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> part = Map.of("text", text);
            Map<String, Object> content = Map.of("parts", List.of(part));
            Map<String, Object> requestPayload = Map.of(
                    "model", "models/" + embeddingModel,
                    "content", content,
                    "outputDimensionality", 768
            );

            String requestBody = objectMapper.writeValueAsString(requestPayload);
            String url = String.format(
                    "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                    embeddingModel, geminiApiKey.trim()
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(httpResponse.body());
                JsonNode valuesNode = root.path("embedding").path("values");
                if (valuesNode.isArray()) {
                    List<String> values = new ArrayList<>();
                    for (JsonNode val : valuesNode) {
                        values.add(String.valueOf(val.asDouble()));
                    }
                    return "[" + String.join(",", values) + "]";
                }
            } else if (httpResponse.statusCode() == 429) {
                registrarCotaEsgotada();
            } else {
                log.warn("Erro ao gerar embedding no Gemini (Status {}): {}", httpResponse.statusCode(), httpResponse.body());
            }
        } catch (Exception e) {
            log.error("Exceção ao gerar embedding: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Indexa em lote as notícias existentes no banco que ainda não possuem registro de embeddings.
     */
    static final int MAX_POR_SYNC = 200;

    public int syncEmbeddings() {
        ragDatabaseInitializer.ensureInitialized();
        log.info("Iniciando sincronização de embeddings RAG...");
        int count = 0;

        try {
            // Só as notícias que ainda não têm vetor: cada uma gasta uma chamada do Gemini
            Set<Long> jaIndexadas = new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT noticia_id FROM rag_noticias_embeddings WHERE embedding IS NOT NULL", Long.class));
            List<NewsEntity> pendentes = newsRepository.findAll().stream()
                    .filter(n -> !jaIndexadas.contains(n.getId()))
                    .limit(MAX_POR_SYNC)
                    .toList();
            log.info("{} notícias sem vetor (máximo {} por rodada).", pendentes.size(), MAX_POR_SYNC);
            for (NewsEntity news : pendentes) {
                if (cotaEsgotada()) {
                    log.warn("Cota do Gemini esgotada: o restante fica para a próxima sincronização.");
                    break;
                }
                String semanticContent = String.format(
                        "[%s] [%s] Título: %s. Resumo: %s. Fonte: %s.",
                        news.getCategoria(),
                        news.getEstado() != null ? news.getEstado() : "BR",
                        news.getTitulo(),
                        news.getResumo(),
                        news.getFonte()
                );

                String metadataJson = objectMapper.writeValueAsString(Map.of(
                        "id", news.getId(),
                        "fonte", news.getFonte(),
                        "categoria", news.getCategoria().name(),
                        "url", news.getUrl(),
                        "estado", news.getEstado() != null ? news.getEstado() : "",
                        "municipio", news.getMunicipio() != null ? news.getMunicipio() : ""
                ));

                String embeddingVector = generateEmbedding(semanticContent);

                if (embeddingVector != null) {
                    try {
                        jdbcTemplate.update("""
                            INSERT INTO rag_noticias_embeddings (noticia_id, conteudo_semantico, metadados, embedding)
                            VALUES (?, ?, ?::jsonb, ?::vector)
                            ON CONFLICT (noticia_id) DO UPDATE SET
                                conteudo_semantico = EXCLUDED.conteudo_semantico,
                                metadados = EXCLUDED.metadados,
                                embedding = EXCLUDED.embedding
                        """, news.getId(), semanticContent, metadataJson, embeddingVector);
                        count++;
                    } catch (Exception ex) {
                        // Fallback relacional
                        jdbcTemplate.update("""
                            INSERT INTO rag_noticias_embeddings (noticia_id, conteudo_semantico, metadados, embedding)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (noticia_id) DO UPDATE SET
                                conteudo_semantico = EXCLUDED.conteudo_semantico,
                                metadados = EXCLUDED.metadados
                        """, news.getId(), semanticContent, metadataJson, embeddingVector);
                        count++;
                    }
                } else {
                    // Sem chave da API: indexa o conteúdo semântico para fallback
                    try {
                        jdbcTemplate.update("""
                            INSERT INTO rag_noticias_embeddings (noticia_id, conteudo_semantico, metadados, embedding)
                            VALUES (?, ?, ?::jsonb, NULL)
                            ON CONFLICT (noticia_id) DO NOTHING
                        """, news.getId(), semanticContent, metadataJson);
                        count++;
                    } catch (Exception ex) {
                        // H2 fallback
                        jdbcTemplate.update("""
                            INSERT INTO rag_noticias_embeddings (noticia_id, conteudo_semantico, metadados, embedding)
                            VALUES (?, ?, ?, NULL)
                            ON CONFLICT (noticia_id) DO NOTHING
                        """, news.getId(), semanticContent, metadataJson);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro durante sincronização de embeddings: {}", e.getMessage());
        }

        log.info("Sincronização de embeddings finalizada. {} matérias processadas.", count);
        return count;
    }

    /**
     * Retorna o status da infraestrutura de IA e banco vetorial.
     */
    public AiStatusResponse getStatus() {
        long totalNews = newsRepository.count();
        long totalEmbeddings = 0;
        boolean pgvectorActive = false;
        String diagnostico = br.com.ecowatch.read_model_service.infrastructure.config.RagDatabaseInitializer.diagnostico;

        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_noticias_embeddings", Long.class);
            if (count != null) totalEmbeddings = count;

            // Testa se pgvector está operacional
            jdbcTemplate.execute("SELECT '[1,2,3]'::vector;");
            pgvectorActive = true;
        } catch (Exception e) {
            log.debug("pgvector não ativo: {}", e.getMessage());
            diagnostico = e.getMessage();
        }

        return AiStatusResponse.builder()
                .pgvectorAtivo(pgvectorActive)
                .totalNoticias(totalNews)
                .totalEmbeddings(totalEmbeddings)
                .geminiConfigurado(isGeminiConfigured())
                .modeloPadrao(geminiModel)
                .diagnostico(pgvectorActive ? "ok" : (diagnostico == null ? null : diagnostico.substring(0, Math.min(300, diagnostico.length()))))
                .build();
    }

    public boolean isGeminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.trim().isBlank();
    }

    public static String sanitizeLatex(String text) {
        if (text == null || text.isBlank()) return text;
        String s = text;
        // Substitui notações químicas LaTeX pelos caracteres subscritos unicode
        s = s.replaceAll("(?i)\\\\text\\{CH\\}_?4", "CH₄");
        s = s.replaceAll("(?i)\\\\text\\{CO\\}_?2e", "CO₂e");
        s = s.replaceAll("(?i)\\\\text\\{CO\\}_?2", "CO₂");
        s = s.replaceAll("(?i)\\\\text\\{N\\}_?2O", "N₂O");
        s = s.replaceAll("(?i)CH_4", "CH₄");
        s = s.replaceAll("(?i)CO_2e", "CO₂e");
        s = s.replaceAll("(?i)CO_2", "CO₂");
        s = s.replaceAll("(?i)N_2O", "N₂O");

        // Desembrulha \text{...}
        s = s.replaceAll("\\\\text\\{([^\\}]+)\\}", "$1");

        // Desembrulha blocos matemáticos $...$ ou $$...$$
        s = s.replaceAll("\\$\\$([^\\$]+)\\$\\$", "$1");
        s = s.replaceAll("\\$([^\\$]+)\\$", "$1");

        // Remove quaisquer cifrões ou barras invertidas órfãs de LaTeX
        s = s.replace("$", "");
        return s;
    }
}
