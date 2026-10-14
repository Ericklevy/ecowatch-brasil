package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import br.com.ecowatch.read_model_service.infrastructure.entity.NewsEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsIngestionService {

    private final NewsRepository newsRepository;
    private final NewsClassifier newsClassifier;
    private final JdbcTemplate jdbcTemplate;

    public record FeedSource(String name, String url, boolean isEnvironmentalFeed) {}

    private static final List<FeedSource> SOURCES = List.of(
            new FeedSource("Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/meio-ambiente/feed.xml", true),
            new FeedSource("G1 Meio Ambiente", "https://g1.globo.com/rss/g1/natureza/", true),
            new FeedSource("Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml", false),
            new FeedSource("Agência Gov", "https://agenciagov.ebc.com.br/noticias/feed.xml", false)
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Executa a sincronização programada 3 vezes ao dia (07h, 13h, 19h no fuso de Brasília).
     */
    @Scheduled(cron = "0 0 7,13,19 * * *", zone = "America/Sao_Paulo")
    public void scheduledSync() {
        log.info("Iniciando coleta automática agendada de notícias ambientais...");
        int imported = syncAllSources();
        log.info("Coleta agendada concluída com sucesso. Novas notícias importadas: {}", imported);
    }

    /**
     * Ao subir a aplicação, purga notícias irrelevantes e sincroniza fontes se necessário.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread.ofVirtual().start(() -> {
            try {
                int purged = purgeIrrelevantExistingNews();
                if (purged > 0) {
                    log.info("Limpeza de inicialização: {} notícias irrelevantes removidas.", purged);
                }

                long count = newsRepository.count();
                if (count < 5) {
                    log.info("Base de notícias vazia ou com poucas notícias ({}), iniciando carga inicial...", count);
                    int imported = syncAllSources();
                    log.info("Carga inicial concluída. Total importado: {}", imported);
                }
            } catch (Exception e) {
                log.warn("Falha não bloqueante na inicialização de notícias: {}", e.getMessage());
            }
        });
    }

    /**
     * Purga matérias do banco que não atendem às regras estritas ou contêm ruído da blacklist.
     */
    public int purgeIrrelevantExistingNews() {
        // 1. Remove a check constraint antiga do PostgreSQL para permitir DEFORESTATION e novas categorias
        try {
            jdbcTemplate.execute("ALTER TABLE noticias_ambientais DROP CONSTRAINT IF EXISTS noticias_ambientais_categoria_check");
        } catch (Exception e) {
            log.warn("Aviso ao remover constraint de categoria no banco: {}", e.getMessage());
        }

        // 2. Remoção direta via SQL de matérias de ruído político e trivialidades
        int directPurged = 0;
        try {
            directPurged = jdbcTemplate.update(
                    "DELETE FROM noticias_ambientais WHERE " +
                    "LOWER(titulo) LIKE '%urna%' OR LOWER(titulo) LIKE '%eleiç%' OR " +
                    "LOWER(titulo) LIKE '%2º turno%' OR LOWER(titulo) LIKE '%segundo turno%' OR " +
                    "LOWER(titulo) LIKE '%candidat%' OR LOWER(titulo) LIKE '%stf%' OR " +
                    "LOWER(titulo) LIKE '%marco temporal%' OR LOWER(titulo) LIKE '%lixo eletrônico%' OR " +
                    "LOWER(titulo) LIKE '%florada do ipê%' OR LOWER(titulo) LIKE '%solzinho%' OR " +
                    "LOWER(titulo) LIKE '%partido%'"
            );
            if (directPurged > 0) {
                log.info("SQL direto removeu {} notícias de ruído político e trivialidades.", directPurged);
            }
        } catch (Exception e) {
            log.warn("Aviso na limpeza direta por SQL: {}", e.getMessage());
        }

        // 3. Varredura semântica fina: remove matérias fora do domínio e reclassifica as válidas
        var all = newsRepository.findAll();
        var toDelete = new java.util.ArrayList<NewsEntity>();
        var toUpdate = new java.util.ArrayList<NewsEntity>();

        for (var news : all) {
            var validCat = newsClassifier.classify(news.getTitulo(), news.getResumo(), false);
            if (validCat.isEmpty()) {
                toDelete.add(news);
            } else if (news.getCategoria() != validCat.get()) {
                news.setCategoria(validCat.get());
                toUpdate.add(news);
            }
        }

        if (!toDelete.isEmpty()) {
            newsRepository.deleteAll(toDelete);
            log.info("Removidas mais {} notícias pelo classificador semântico.", toDelete.size());
        }
        if (!toUpdate.isEmpty()) {
            newsRepository.saveAll(toUpdate);
            log.info("Reclassificadas {} notícias para categorias semânticas corretas.", toUpdate.size());
        }
        return directPurged + toDelete.size();
    }

    /**
     * Executa a sincronização de todas as fontes RSS e retorna o total de novas notícias inseridas.
     */
    public int syncAllSources() {
        int totalImported = 0;
        for (FeedSource source : SOURCES) {
            try {
                int imported = fetchAndProcessFeed(source);
                totalImported += imported;
            } catch (Exception e) {
                log.warn("Erro ao processar feed '{}' ({}): {}", source.name(), source.url(), e.getMessage());
            }
        }
        return totalImported;
    }

    private int fetchAndProcessFeed(FeedSource source) {
        log.info("Buscando feed RSS: {} - {}", source.name(), source.url());
        int importedCount = 0;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source.url()))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) EcoWatchBot/1.0")
                    .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    .header("Accept-Encoding", "gzip, deflate")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.warn("Feed '{}' retornou status HTTP {}", source.url(), response.statusCode());
                return 0;
            }

            InputStream responseStream = response.body();
            String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
            if (contentEncoding.equalsIgnoreCase("gzip")) {
                responseStream = new GZIPInputStream(responseStream);
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(false);
            // Proteções contra XXE
            try {
                dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {}

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(responseStream);
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                String title = getTagValue(item, "title");
                String link = getTagValue(item, "link");
                String guid = getTagValue(item, "guid");
                String description = getTagValue(item, "description");
                String pubDateStr = getTagValue(item, "pubDate");

                String canonicalUrl = (link != null && !link.isBlank()) ? link.trim() : (guid != null ? guid.trim() : "");
                if (canonicalUrl.isBlank() || title == null || title.isBlank()) {
                    continue;
                }

                // Evita duplicatas pela URL
                if (newsRepository.existsByUrl(canonicalUrl)) {
                    continue;
                }

                // Classifica a matéria
                Optional<NewsCategory> categoryOpt = newsClassifier.classify(title, description, source.isEnvironmentalFeed());
                if (categoryOpt.isEmpty()) {
                    continue;
                }

                Instant dataHora = parsePubDate(pubDateStr);
                String resumo = newsClassifier.cleanAndSummarize(description != null && !description.isBlank() ? description : title, 200);
                NewsClassifier.LocationInfo location = newsClassifier.extractLocation(title, description);

                NewsEntity newsEntity = NewsEntity.builder()
                        .titulo(title.trim())
                        .resumo(resumo)
                        .fonte(source.name())
                        .url(canonicalUrl)
                        .dataHora(dataHora)
                        .categoria(categoryOpt.get())
                        .municipio(location.municipio())
                        .estado(location.estado())
                        .dataCriacao(Instant.now())
                        .build();

                newsRepository.save(newsEntity);
                importedCount++;
            }

        } catch (Exception e) {
            log.error("Falha ao processar feed {}: {}", source.name(), e.getMessage());
        }

        return importedCount;
    }

    private String getTagValue(Element element, String tagName) {
        NodeList nl = element.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0 && nl.item(0) != null) {
            return nl.item(0).getTextContent();
        }
        return null;
    }

    private Instant parsePubDate(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.isBlank()) {
            return Instant.now();
        }
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(pubDateStr.trim(), Instant::from);
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(pubDateStr.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
            } catch (Exception ignored) {
                return Instant.now();
            }
        }
    }
}
