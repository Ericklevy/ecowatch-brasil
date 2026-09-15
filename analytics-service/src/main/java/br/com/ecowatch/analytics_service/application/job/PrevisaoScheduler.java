package br.com.ecowatch.analytics_service.application.job;

import br.com.ecowatch.analytics_service.application.service.PrevisaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrevisaoScheduler {

    private final PrevisaoService previsaoService;

    // Executa ao iniciar o serviço para garantir que a tabela previsoes contenha dados
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        log.info("🚀 Serviço de Analytics iniciado: Gerando previsões climáticas iniciais...");
        previsaoService.gerarPrevisoesDiarias();
    }

    // Executa todos os dias às 6h da manhã
    @Scheduled(cron = "0 0 6 * * *")
    public void gerarPrevisoes() {
        log.info("⏰ Iniciando geração de previsões diárias...");
        previsaoService.gerarPrevisoesDiarias();
        log.info("✅ Previsões diárias geradas com sucesso.");
    }
}