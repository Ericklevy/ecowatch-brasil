package br.com.ecowatch.read_model_service.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Lazy(false)
@Component
@RequiredArgsConstructor
public class RagDatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    /** Último problema encontrado ao preparar o pgvector (aparece em /api/v1/ai/status). */
    public static volatile String diagnostico = "não verificado";
    private static volatile boolean initialized = false;

    @PostConstruct
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void init() {
        if (initialized && "ok".equals(diagnostico)) {
            return;
        }
        try {
            // 0. Garante que a tabela noticias_ambientais exista antes de criar chaves estrangeiras
            try {
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS noticias_ambientais (
                        id BIGSERIAL PRIMARY KEY,
                        titulo VARCHAR(500) NOT NULL,
                        resumo TEXT NOT NULL,
                        fonte VARCHAR(100) NOT NULL,
                        url VARCHAR(1000) UNIQUE NOT NULL,
                        data_hora TIMESTAMP NOT NULL,
                        categoria VARCHAR(20) NOT NULL,
                        municipio VARCHAR(100),
                        estado VARCHAR(2),
                        data_criacao TIMESTAMP NOT NULL DEFAULT NOW()
                    );
                """);
            } catch (Exception ex) {
                log.debug("Verificação prévia de noticias_ambientais: {}", ex.getMessage());
            }

            // 1. Tenta ativar a extensão pgvector (nativa no Supabase)
            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector;");
                log.info("✅ Extensão 'vector' (pgvector) inicializada/confirmada no PostgreSQL.");
            } catch (Exception ex) {
                diagnostico = "CREATE EXTENSION falhou: " + ex.getMessage();
                log.warn("Extensão pgvector não pôde ser criada (esperado em H2/local): {}", ex.getMessage());
            }

            // 1b. Se a tabela foi criada antes no modo fallback (coluna TEXT), recria com vector(768).
            //     Os vetores são regerados pela próxima sincronização.
            try {
                String tipo = jdbcTemplate.queryForObject("""
                    SELECT data_type FROM information_schema.columns
                    WHERE table_name = 'rag_noticias_embeddings' AND column_name = 'embedding'
                    """, String.class);
                if (tipo != null && !"USER-DEFINED".equalsIgnoreCase(tipo)) {
                    jdbcTemplate.queryForObject("SELECT '[1,2,3]'::vector::text", String.class); // só segue se o tipo existir
                    log.warn("Tabela rag_noticias_embeddings estava com embedding {}; recriando como vector(768).", tipo);
                    jdbcTemplate.execute("DROP TABLE rag_noticias_embeddings");
                }
            } catch (Exception ex) {
                log.debug("Verificação do tipo da coluna embedding: {}", ex.getMessage());
            }

            // 2. Tenta criar a tabela com coluna vector(768)
            try {
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS rag_noticias_embeddings (
                        id BIGSERIAL PRIMARY KEY,
                        noticia_id BIGINT UNIQUE REFERENCES noticias_ambientais(id) ON DELETE CASCADE,
                        conteudo_semantico TEXT NOT NULL,
                        metadados JSONB,
                        embedding vector(768),
                        criado_em TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                    );
                """);
                log.info("✅ Tabela 'rag_noticias_embeddings' (pgvector 768d) pronta.");
                diagnostico = "ok";
                initialized = true;
            } catch (Exception ex) {
                // Fallback para bancos sem tipo vector (ex: H2 em memória em testes de integração)
                diagnostico = "tabela vetorial não criada: " + ex.getMessage();
                log.info("Criando tabela rag_noticias_embeddings em modo relacional fallback...");
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS rag_noticias_embeddings (
                        id BIGSERIAL PRIMARY KEY,
                        noticia_id BIGINT UNIQUE REFERENCES noticias_ambientais(id) ON DELETE CASCADE,
                        conteudo_semantico TEXT NOT NULL,
                        metadados VARCHAR(4000),
                        embedding TEXT,
                        criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                    );
                """);
                initialized = true;
            }

            // 3. Índice vetorial de cosseno (se pgvector estiver disponível)
            try {
                jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_rag_noticias_embedding 
                    ON rag_noticias_embeddings 
                    USING ivfflat (embedding vector_cosine_ops) 
                    WITH (lists = 100);
                """);
            } catch (Exception ex) {
                log.debug("Aviso ao criar índice ivfflat: {}", ex.getMessage());
            }

        } catch (Exception e) {
            log.error("Erro geral na inicialização do RAG no banco: {}", e.getMessage());
        }
    }

    public void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }
}
