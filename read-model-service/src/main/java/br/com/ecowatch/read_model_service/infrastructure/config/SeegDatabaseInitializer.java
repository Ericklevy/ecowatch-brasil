package br.com.ecowatch.read_model_service.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeegDatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;
    private static volatile boolean initialized = false;

    @PostConstruct
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void init() {
        if (initialized) return;

        try {
            // 1. Garante que a tabela exista com RLS ativado
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS emissoes_municipio (
                    id BIGSERIAL PRIMARY KEY,
                    codigo_ibge VARCHAR(10) NOT NULL,
                    municipio VARCHAR(150) NOT NULL,
                    uf VARCHAR(2) NOT NULL,
                    ano INT NOT NULL,
                    gas VARCHAR(20) NOT NULL,
                    setor VARCHAR(100) NOT NULL,
                    emissao_t DOUBLE PRECISION NOT NULL
                );
            """);

            try {
                jdbcTemplate.execute("ALTER TABLE emissoes_municipio ENABLE ROW LEVEL SECURITY;");
            } catch (Exception ex) {
                log.debug("Aviso ao ativar RLS em emissoes_municipio: {}", ex.getMessage());
            }

            try {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_emissoes_gas_uf_ano ON emissoes_municipio(gas, uf, ano);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_emissoes_mun_ano ON emissoes_municipio(municipio, ano);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_emissoes_gas_ano ON emissoes_municipio(gas, ano);");
            } catch (Exception ex) {
                log.debug("Aviso ao criar índices em emissoes_municipio: {}", ex.getMessage());
            }

            initialized = true;

            // 2. Carregamento sob demanda em background (Virtual Thread)
            Thread.ofVirtual().start(this::carregarCsvSeNecessario);

        } catch (Exception e) {
            log.error("Erro na inicialização da tabela de emissões do SEEG: {}", e.getMessage());
        }
    }

    private void carregarCsvSeNecessario() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM emissoes_municipio", Long.class);
            Integer maxAno = jdbcTemplate.queryForObject("SELECT MAX(ano) FROM emissoes_municipio", Integer.class);
            Long ch4Count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM emissoes_municipio WHERE gas = 'CH4'", Long.class);

            // Importa se a tabela estiver vazia, ano desatualizado ou se faltarem dados de metano nacional (menos de 5000 registros de CH4 indica carga antiga só do PA)
            boolean precisaImportar = (count == null || count == 0 || maxAno == null || maxAno < 2023 || ch4Count == null || ch4Count < 5000);

            if (!precisaImportar) {
                log.info("Base de emissões SEEG já populada: {} registros encontrados (último ano: {}, registros CH4: {}).", count, maxAno, ch4Count);
                return;
            }

            if (count != null && count > 0) {
                log.info("Substituindo base de emissões SEEG incompleta ({} registros, {} CH4) pela carga nacional consolidada...", count, ch4Count);
                jdbcTemplate.execute("TRUNCATE TABLE emissoes_municipio;");
            }

            Resource resource = new ClassPathResource("data/seeg_municipios_5anos.csv");
            if (!resource.exists()) {
                log.warn("Arquivo de dados SEEG 'data/seeg_municipios_5anos.csv' não encontrado no classpath.");
                return;
            }

            log.info("Iniciando importação de dados SEEG (Coleção 14.0)...");
            long startTime = System.currentTimeMillis();

            String sql = "INSERT INTO emissoes_municipio (codigo_ibge, municipio, uf, ano, gas, setor, emissao_t) VALUES (?, ?, ?, ?, ?, ?, ?)";
            List<Object[]> batch = new ArrayList<>(2000);
            long totalImported = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine(); // ignora cabeçalho
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    // Parse CSV: codigo_ibge,"municipio",uf,ano,gas,"setor",emissao_t
                    String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    if (parts.length < 7) continue;

                    String ibge = parts[0].replace("\"", "").trim();
                    String mun = parts[1].replace("\"", "").trim();
                    String uf = parts[2].replace("\"", "").trim();
                    int ano = Integer.parseInt(parts[3].replace("\"", "").trim());
                    String gas = parts[4].replace("\"", "").trim();
                    String setor = parts[5].replace("\"", "").trim();
                    double emissao = Double.parseDouble(parts[6].replace("\"", "").trim());

                    batch.add(new Object[]{ibge, mun, uf, ano, gas, setor, emissao});

                    if (batch.size() >= 2000) {
                        jdbcTemplate.batchUpdate(sql, batch);
                        totalImported += batch.size();
                        batch.clear();
                    }
                }

                if (!batch.isEmpty()) {
                    jdbcTemplate.batchUpdate(sql, batch);
                    totalImported += batch.size();
                    batch.clear();
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ Importação do SEEG concluída com sucesso: {} linhas inseridas em {} ms.", totalImported, elapsed);

        } catch (Exception e) {
            log.error("Erro durante a carga do CSV de emissões SEEG: {}", e.getMessage(), e);
        }
    }
}
