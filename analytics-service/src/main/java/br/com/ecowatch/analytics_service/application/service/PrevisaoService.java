package br.com.ecowatch.analytics_service.application.service;

import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoEntity;
import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoRepository;
import br.com.ecowatch.analytics_service.util.CityNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrevisaoService {

    private final PrevisaoRepository repository;

    // Lista de cidades monitoradas (padronizadas)
    private static final List<String> CIDADES_BASE = List.of(
            "Brasília", "Rio de Janeiro", "São Paulo", "Salvador",
            "Recife", "Porto Alegre", "Curitiba", "Belo Horizonte", "Fortaleza", "Manaus"
    );

    private final Random random = new Random();

    /**
     * Gera uma previsão para cada cidade (simulação simples).
     * Em produção, você usaria um modelo de ML (Spark, etc.)
     */
    public void gerarPrevisoesDiarias() {
        LocalDate hoje = LocalDate.now();
        LocalDate amanha = hoje.plusDays(1);
        LocalDate depois = hoje.plusDays(2);

        List<String> cidadesPadronizadas = CIDADES_BASE.stream()
                .map(CityNameNormalizer::normalize)
                .distinct()
                .collect(Collectors.toList());

        for (String cidade : cidadesPadronizadas) {
            // Gera previsões para amanhã e depois de amanhã
            gerarPrevisaoPara(cidade, amanha);
            gerarPrevisaoPara(cidade, depois);
        }

        log.info("✅ Previsões geradas para {} cidades", cidadesPadronizadas.size());
    }

    private void gerarPrevisaoPara(String municipio, LocalDate data) {
        String municipioNormalizado = CityNameNormalizer.normalize(municipio);

        // Verifica se já existe previsão para essa data (evita duplicidade)
        var existentes = repository.findByMunicipioAndDataPrevisaoAfter(municipioNormalizado, data.minusDays(1));
        if (existentes.stream().anyMatch(p -> p.getDataPrevisao().equals(data))) {
            log.debug("⏩ Previsão para {} em {} já existe, pulando...", municipioNormalizado, data);
            return;
        }

        // Simula dados (em produção, viriam de um modelo preditivo)
        double tempBase = 25.0 + random.nextDouble() * 10;
        double precipBase = random.nextDouble() * 15;
        double umidadeBase = 60.0 + random.nextDouble() * 30;
        int riscoIncendio = random.nextInt(60) + 10;
        int riscoDengue = (int) (umidadeBase > 70 && tempBase > 28 ? 40 + random.nextInt(40) : random.nextInt(20));

        PrevisaoEntity entity = new PrevisaoEntity(
                municipioNormalizado,
                data,
                Math.round(tempBase * 100.0) / 100.0,
                Math.round(precipBase * 100.0) / 100.0,
                Math.round(umidadeBase * 100.0) / 100.0,
                riscoIncendio,
                riscoDengue
        );

        repository.save(entity);
        log.info("📊 Previsão salva para {} em {}: temp={}°C, precip={}mm, umidade={}%",
                municipioNormalizado, data, entity.getTemperaturaPrevista(),
                entity.getPrecipitacaoPrevista(), entity.getUmidadePrevista());
    }
}