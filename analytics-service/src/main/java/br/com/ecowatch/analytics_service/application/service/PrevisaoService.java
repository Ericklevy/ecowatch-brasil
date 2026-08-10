package br.com.ecowatch.analytics_service.application.service;

import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoEntity;
import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrevisaoService {

    private final PrevisaoRepository repository;

    // Lista de cidades monitoradas (pode vir do banco ou de uma propriedade)
    private static final List<String> CIDADES = List.of("Brasília", "Rio de Janeiro", "São Paulo");

    private final Random random = new Random();

    /**
     * Gera uma previsão para cada cidade (simulação simples).
     * Em produção, você usaria um modelo de ML (Spark, etc.)
     */
    public void gerarPrevisoesDiarias() {
        LocalDate hoje = LocalDate.now();
        LocalDate amanha = hoje.plusDays(1);
        LocalDate depois = hoje.plusDays(2);

        for (String cidade : CIDADES) {
            // Gera previsões para amanhã e depois de amanhã
            gerarPrevisaoPara(cidade, amanha);
            gerarPrevisaoPara(cidade, depois);
        }

        log.info("✅ Previsões geradas para {} cidades", CIDADES.size());
    }

    private void gerarPrevisaoPara(String municipio, LocalDate data) {
        // Verifica se já existe previsão para essa data (evita duplicidade)
        var existentes = repository.findByMunicipioAndDataPrevisaoAfter(municipio, data.minusDays(1));
        if (existentes.stream().anyMatch(p -> p.getDataPrevisao().equals(data))) {
            log.debug("⏩ Previsão para {} em {} já existe, pulando...", municipio, data);
            return;
        }

        // Simula dados (em produção, viriam de um modelo preditivo)
        double tempBase = 25.0 + random.nextDouble() * 10;
        double precipBase = random.nextDouble() * 15;
        double umidadeBase = 60.0 + random.nextDouble() * 30;
        int riscoIncendio = random.nextInt(60) + 10;
        int riscoDengue = (int) (umidadeBase > 70 && tempBase > 28 ? 40 + random.nextInt(40) : random.nextInt(20));

        PrevisaoEntity entity = new PrevisaoEntity(
                municipio,
                data,
                Math.round(tempBase * 100.0) / 100.0,
                Math.round(precipBase * 100.0) / 100.0,
                Math.round(umidadeBase * 100.0) / 100.0,
                riscoIncendio,
                riscoDengue
        );

        repository.save(entity);
        log.info("📊 Previsão salva para {} em {}: temp={}°C, precip={}mm, umidade={}%",
                municipio, data, entity.getTemperaturaPrevista(),
                entity.getPrecipitacaoPrevista(), entity.getUmidadePrevista());
    }
}