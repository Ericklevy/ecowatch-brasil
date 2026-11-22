package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.infrastructure.entity.EmissorClimateTraceEntity;
import br.com.ecowatch.read_model_service.infrastructure.persistence.EmissorClimateTraceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClimateTraceIngestionService {

    private static final String API_BASE = "https://api.climatetrace.org/v6/assets?countries=BRA&limit=200";

    private final EmissorClimateTraceRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @PostConstruct
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initDatabaseAndCheckSync() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS emissores_climatetrace (
                    id BIGSERIAL PRIMARY KEY,
                    asset_id BIGINT NOT NULL,
                    nome VARCHAR(255) NOT NULL,
                    setor VARCHAR(100) NOT NULL,
                    sub_setor VARCHAR(100),
                    latitude DOUBLE PRECISION NOT NULL,
                    longitude DOUBLE PRECISION NOT NULL,
                    gas VARCHAR(20) NOT NULL,
                    emissao_t DOUBLE PRECISION NOT NULL,
                    ano INT NOT NULL,
                    atualizado_em TIMESTAMP NOT NULL,
                    CONSTRAINT uk_asset_gas UNIQUE (asset_id, gas)
                );
            """);

            try {
                jdbcTemplate.execute("ALTER TABLE emissores_climatetrace ENABLE ROW LEVEL SECURITY;");
            } catch (Exception ex) {
                log.debug("Aviso ao ativar RLS em emissores_climatetrace: {}", ex.getMessage());
            }

            try {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_emissores_gas ON emissores_climatetrace(gas);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_emissores_setor ON emissores_climatetrace(setor);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_emissores_emissao ON emissores_climatetrace(emissao_t);");
            } catch (Exception ex) {
                log.debug("Aviso ao criar índices em emissores_climatetrace: {}", ex.getMessage());
            }

            long count = repository.count();
            log.info("📊 emissores_climatetrace carregados no banco: {}", count);
            if (count == 0) {
                Thread.ofVirtual().name("climatetrace-initial-sync").start(this::sincronizarFontes);
            }
        } catch (Exception e) {
            log.error("Erro ao inicializar tabela emissores_climatetrace: {}", e.getMessage(), e);
        }
    }

    /**
     * Sincronização mensal agendada (dia 1 de cada mês às 04:00 da manhã).
     */
    @Scheduled(cron = "0 0 4 1 * ?", zone = "America/Sao_Paulo")
    public void sincronizarMensalmente() {
        log.info("⏰ Iniciando sincronização mensal agendada de fontes do Climate TRACE...");
        sincronizarFontes();
    }

    /**
     * Executa o download e a persistência dos top emissores de CO2e e CH4 no Brasil.
     */
    public void sincronizarFontes() {
        log.info("🛰️ Baixando top fontes emissoras do Brasil via API pública Climate TRACE...");
        try {
            int salvosCo2e = baixarEPersistir("co2e_100yr", "CO2e");
            int salvosCh4 = baixarEPersistir("ch4", "CH4");
            log.info("✅ Sincronização Climate TRACE finalizada: {} fontes CO2e e {} fontes CH4.", salvosCo2e, salvosCh4);
        } catch (Exception e) {
            log.error("❌ Falha na sincronização do Climate TRACE: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public int baixarEPersistir(String gasApi, String gasDb) {
        String url = API_BASE + "&gas=" + gasApi;
        log.info("Fetching Climate TRACE: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Climate TRACE API retornou status {}: {}", response.statusCode(), response.body());
                return 0;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode assetsNode = root.path("assets");
            if (!assetsNode.isArray()) {
                log.warn("Campo 'assets' não encontrado ou não é array na resposta do Climate TRACE.");
                return 0;
            }

            List<EmissorClimateTraceEntity> entidadesParaSalvar = new ArrayList<>();
            LocalDateTime agora = LocalDateTime.now();

            for (JsonNode assetNode : assetsNode) {
                try {
                    Long assetId = assetNode.path("Id").asLong();
                    String nome = assetNode.path("Name").asText("Fonte sem nome");
                    String sector = assetNode.path("Sector").asText("desconhecido");
                    String assetType = assetNode.path("AssetType").asText(null);

                    JsonNode geom = assetNode.path("Centroid").path("Geometry");
                    if (!geom.isArray() || geom.size() < 2) {
                        continue;
                    }
                    double lon = geom.get(0).asDouble();
                    double lat = geom.get(1).asDouble();

                    // Buscar quantidade de emissão
                    double emissao = 0.0;
                    JsonNode summary = assetNode.path("EmissionsSummary");
                    if (summary.isArray()) {
                        for (JsonNode item : summary) {
                            if (gasApi.equalsIgnoreCase(item.path("Gas").asText())) {
                                emissao = item.path("EmissionsQuantity").asDouble(0.0);
                                break;
                            }
                        }
                    }

                    if (emissao <= 0.0) {
                        continue;
                    }

                    int ano = extrairAnoMaisRecente(assetNode);

                    EmissorClimateTraceEntity entity = repository.findByAssetIdAndGas(assetId, gasDb)
                            .orElseGet(EmissorClimateTraceEntity::new);

                    entity.setAssetId(assetId);
                    entity.setNome(nome);
                    entity.setSetor(sector);
                    entity.setSubSetor(assetType);
                    entity.setLatitude(lat);
                    entity.setLongitude(lon);
                    entity.setGas(gasDb);
                    entity.setEmissaoT(emissao);
                    entity.setAno(ano);
                    entity.setAtualizadoEm(agora);

                    entidadesParaSalvar.add(entity);
                } catch (Exception itemEx) {
                    log.debug("Erro ao processar item do Climate TRACE: {}", itemEx.getMessage());
                }
            }

            if (!entidadesParaSalvar.isEmpty()) {
                repository.saveAll(entidadesParaSalvar);
            }
            return entidadesParaSalvar.size();

        } catch (Exception e) {
            log.error("Erro ao baixar dados do Climate TRACE para {}: {}", gasDb, e.getMessage(), e);
            return 0;
        }
    }

    private int extrairAnoMaisRecente(JsonNode assetNode) {
        JsonNode sectorRanks = assetNode.path("SectorRanks");
        int maxAno = 2024;
        if (sectorRanks.isObject()) {
            Iterator<String> fieldNames = sectorRanks.fieldNames();
            while (fieldNames.hasNext()) {
                try {
                    int ano = Integer.parseInt(fieldNames.next());
                    if (ano > maxAno && ano <= 2026) {
                        maxAno = ano;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxAno;
    }

    public static String traduzirSetor(String setorOriginal) {
        if (setorOriginal == null) return "Outros";
        String s = setorOriginal.toLowerCase();
        if (s.contains("power") || s.contains("electricity")) return "Energia Elétrica (Termelétricas)";
        if (s.contains("waste") || s.contains("disposal") || s.contains("landfill")) return "Resíduos Sólidos e Aterros";
        if (s.contains("manufacturing") || s.contains("steel") || s.contains("aluminum") || s.contains("cement")) return "Indústria e Metalurgia";
        if (s.contains("oil") || s.contains("gas") || s.contains("fossil") || s.contains("refin")) return "Petróleo e Gás Natural";
        if (s.contains("mineral") || s.contains("mining")) return "Mineração e Extração Mineral";
        if (s.contains("agriculture") || s.contains("cattle") || s.contains("enteric") || s.contains("livestock")) return "Agropecuária e Pecuária";
        if (s.contains("fire") || s.contains("forest") || s.contains("land-use")) return "Uso da Terra e Queimadas";
        if (s.contains("transport")) return "Transportes";
        return setorOriginal;
    }
}
