package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.*;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${gemini.api.embedding-model:text-embedding-004}")
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
        long startTime = System.currentTimeMillis();
        String pergunta = request.getPergunta().trim();
        int topK = request.getTopK() != null && request.getTopK() > 0 ? Math.min(request.getTopK(), 8) : 4;

        log.info("Recebida pergunta RAG: '{}' (Cat: {}, UF: {}, TopK: {})",
                pergunta, request.getCategoria(), request.getEstado(), topK);

        // 1. Tenta gerar o embedding da pergunta se a API do Gemini estiver configurada
        String queryVectorStr = null;
        if (isGeminiConfigured()) {
            queryVectorStr = generateEmbedding(pergunta);
        }

        // 2. Busca notícias similares no banco (via pgvector ou fallback semântico)
        List<RagSourceItem> fontes = searchSimilarNews(pergunta, queryVectorStr, request.getCategoria(), request.getEstado(), topK);

        // 3. Formulação da Resposta
        String respostaFinal;
        String modeloUtilizado;

        if (fontes.isEmpty()) {
            respostaFinal = "Não foram encontradas notícias ou comunicados oficiais recentes que correspondam a essa busca nos registros do EcoWatch Brasil. Tente buscar com outros termos ou verificar a central de alertas.";
            modeloUtilizado = "ecowatch-rag-empty";
        } else if (isGeminiConfigured()) {
            respostaFinal = generateLlmAnswer(pergunta, fontes);
            modeloUtilizado = geminiModel;
        } else {
            // Modo factual inteligente sem chave da API Gemini configurada
            respostaFinal = generateFactualSummary(pergunta, fontes);
            modeloUtilizado = "ecowatch-rag-synthesizer";
        }

        long elapsedMs = System.currentTimeMillis() - startTime;

        return AiAskResponse.builder()
                .pergunta(pergunta)
                .resposta(respostaFinal)
                .fontes(fontes)
                .totalFontesEncontradas(fontes.size())
                .modelo(modeloUtilizado)
                .tempoRespostaMs(elapsedMs)
                .build();
    }

    /**
     * Busca as notícias mais relevantes no banco de dados.
     */
    private List<RagSourceItem> searchSimilarNews(String queryText, String vectorStr, NewsCategory categoria, String estado, int limit) {
        // Se temos vetor, tenta busca vetorial de cosseno no pgvector
        if (vectorStr != null) {
            try {
                String sql = """
                    SELECT r.noticia_id, n.titulo, n.resumo, n.fonte, n.url, n.data_hora, n.categoria, n.municipio, n.estado,
                           (1 - (r.embedding <=> ?::vector)) as similaridade
                    FROM rag_noticias_embeddings r
                    JOIN noticias_ambientais n ON r.noticia_id = n.id
                    WHERE (? IS NULL OR n.categoria = ?)
                      AND (? IS NULL OR n.estado = ?)
                    ORDER BY r.embedding <=> ?::vector ASC
                    LIMIT ?
                """;

                String catStr = categoria != null ? categoria.name() : null;
                String ufStr = estado != null && !estado.isBlank() ? estado.trim().toUpperCase() : null;

                List<RagSourceItem> items = jdbcTemplate.query(
                        sql,
                        ps -> {
                            ps.setString(1, vectorStr);
                            ps.setString(2, catStr);
                            ps.setString(3, catStr);
                            ps.setString(4, ufStr);
                            ps.setString(5, ufStr);
                            ps.setString(6, vectorStr);
                            ps.setInt(7, limit);
                        },
                        (rs, rowNum) -> RagSourceItem.builder()
                                .id(rs.getLong("noticia_id"))
                                .titulo(rs.getString("titulo"))
                                .resumo(rs.getString("resumo"))
                                .fonte(rs.getString("fonte"))
                                .url(rs.getString("url"))
                                .dataHora(rs.getTimestamp("data_hora").toInstant())
                                .categoria(NewsCategory.valueOf(rs.getString("categoria")))
                                .municipio(rs.getString("municipio"))
                                .estado(rs.getString("estado"))
                                .similaridade(Math.round(rs.getDouble("similaridade") * 100.0) / 100.0)
                                .build()
                );

                if (!items.isEmpty()) {
                    return items;
                }
            } catch (Exception e) {
                log.warn("Busca vetorial via pgvector não retornou resultados ou falhou: {}. Usando busca contextual...", e.getMessage());
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
                  AND (? IS NULL OR estado = ?)
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
    private String generateLlmAnswer(String pergunta, List<RagSourceItem> fontes) {
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

            String prompt = String.format("""
                Você é o EcoWatch Brasil AI, especialista em monitoramento climático e desastres ambientais.
                
                DIRETRIZES OBRIGATÓRIAS:
                1. Responda em Português do Brasil com clareza, empatia e tom técnico/informativo.
                2. Baseie sua resposta EXCLUSIVAMENTE nas matérias oficiais fornecidas abaixo no CONTEXTO.
                3. Cite expressamente os órgãos ou fontes (ex: Defesa Civil, Agência Brasil, Inmet, INPE), datas e locais.
                4. Sempre mencione que as informações são baseadas nos relatórios oficiais do sistema.
                
                CONTEXTO DE NOTÍCIAS OFICIAIS:
                %s
                
                PERGUNTA DO USUÁRIO:
                %s
                """, contextBuilder, pergunta);

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
            } else {
                log.warn("Gemini API retornou status {}: {}", httpResponse.statusCode(), httpResponse.body());
            }
        } catch (Exception e) {
            log.error("Erro ao chamar API do Gemini para gerar resposta: {}", e.getMessage());
        }

        // Se falhar a chamada externa ao Gemini, retorna a síntese factual estruturada
        return generateFactualSummary(pergunta, fontes);
    }

    /**
     * Síntese factual inteligente sem alucinações para quando não há LLM externo ou a chave não foi definida.
     */
    private String generateFactualSummary(String pergunta, List<RagSourceItem> fontes) {
        StringBuilder sb = new StringBuilder();
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
                    "content", content
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
    public int syncEmbeddings() {
        log.info("Iniciando sincronização de embeddings RAG...");
        int count = 0;

        try {
            List<NewsEntity> allNews = newsRepository.findAll();
            for (NewsEntity news : allNews) {
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

        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_noticias_embeddings", Long.class);
            if (count != null) totalEmbeddings = count;

            // Testa se pgvector está operacional
            jdbcTemplate.execute("SELECT '[1,2,3]'::vector;");
            pgvectorActive = true;
        } catch (Exception e) {
            log.debug("pgvector não ativo: {}", e.getMessage());
        }

        return AiStatusResponse.builder()
                .pgvectorAtivo(pgvectorActive)
                .totalNoticias(totalNews)
                .totalEmbeddings(totalEmbeddings)
                .geminiConfigurado(isGeminiConfigured())
                .modeloPadrao(geminiModel)
                .build();
    }

    public boolean isGeminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.trim().isBlank();
    }
}
