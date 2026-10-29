package br.com.ecowatch.analytics_service.application.service;

import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoEntity;
import br.com.ecowatch.analytics_service.infrastructure.persistence.PrevisaoRepository;
import br.com.ecowatch.analytics_service.util.CityNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
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

        List<PrevisaoEntity> todasExistentes = repository.findByMunicipioInAndDataPrevisaoIn(
                cidadesPadronizadas,
                List.of(amanha, depois)
        );
        List<PrevisaoEntity> novasPrevisoes = new ArrayList<>();

        for (String cidade : cidadesPadronizadas) {
            novasPrevisoes.addAll(gerarPrevisaoPara(cidade, amanha, todasExistentes));
            novasPrevisoes.addAll(gerarPrevisaoPara(cidade, depois, todasExistentes));
        }

        if (!novasPrevisoes.isEmpty()) {
            repository.saveAll(novasPrevisoes);
            for (PrevisaoEntity entity : novasPrevisoes) {
                log.info("📊 Previsão gerada e salva no lote para {} em {}: temp={}°C, precip={}mm, umidade={}%",
                        entity.getMunicipio(), entity.getDataPrevisao(), entity.getTemperaturaPrevista(),
                        entity.getPrecipitacaoPrevista(), entity.getUmidadePrevista());
            }
        }

        log.info("✅ Previsões geradas para {} cidades", cidadesPadronizadas.size());
    }

    private List<PrevisaoEntity> gerarPrevisaoPara(String municipio, LocalDate data, List<PrevisaoEntity> todasExistentes) {
        String municipioNormalizado = CityNameNormalizer.normalize(municipio);

        // Verifica se já existe previsão para essa data (evita duplicidade)
        boolean jaExiste = todasExistentes.stream()
                .anyMatch(p -> p.getMunicipio().equals(municipioNormalizado) && p.getDataPrevisao().equals(data));

        if (jaExiste) {
            log.debug("⏩ Previsão para {} em {} já existe, pulando...", municipioNormalizado, data);
            return new ArrayList<>();
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

        return List.of(entity);
    }

}