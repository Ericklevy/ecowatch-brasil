package br.com.ecowatch.alert_service.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE alertas_gerados DROP CONSTRAINT IF EXISTS alertas_gerados_tipo_alerta_check");
            log.info("✅ [DB] Constraint 'alertas_gerados_tipo_alerta_check' removida para suportar novos tipos de alertas!");
        } catch (Exception e) {
            log.warn("⚠️ [DB] Aviso ao tentar remover constraint: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE alertas_gerados ALTER COLUMN mensagem TYPE TEXT");
            log.info("✅ [DB] Coluna 'mensagem' da tabela 'alertas_gerados' alterada para TEXT!");
        } catch (Exception e) {
            log.warn("⚠️ [DB] Aviso ao alterar coluna mensagem para TEXT: {}", e.getMessage());
        }
    }
}
