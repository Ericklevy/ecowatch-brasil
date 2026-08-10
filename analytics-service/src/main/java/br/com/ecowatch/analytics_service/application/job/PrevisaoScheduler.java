package br.com.ecowatch.analytics_service.application.job;

import br.com.ecowatch.analytics_service.application.service.PrevisaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrevisaoScheduler {

    private final PrevisaoService previsaoService;

    // Executa todos os dias às 6h da manhã
    @Scheduled(cron = "0 0 6 * * *")
    public void gerarPrevisoes() {
        log.info("⏰ Iniciando geração de previsões diárias...");
        previsaoService.gerarPrevisoesDiarias();
        log.info("✅ Previsões diárias geradas com sucesso.");
    }

    // Para testes: executar a cada 30 segundos (descomentar se quiser testar)
    // @Scheduled(fixedDelay = 30000)
    public void gerarPrevisoesTeste() {
        log.info("🧪 Gerando previsões de teste...");
        previsaoService.gerarPrevisoesDiarias();
    }
}