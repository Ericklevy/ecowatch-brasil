package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NewsClassifierTest {

    private NewsClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new NewsClassifier();
    }

    @Test
    @DisplayName("Deve categorizar notícia sobre queimadas e incêndios como FIRE")
    void shouldClassifyFire() {
        Optional<NewsCategory> cat = classifier.classify(
                "Fumaça de queimadas no Pantanal atinge capitais do Centro-Oeste",
                "Brigadistas combatem focos de calor com apoio do Ibama.",
                true
        );
        assertTrue(cat.isPresent());
        assertEquals(NewsCategory.FIRE, cat.get());
    }

    @Test
    @DisplayName("Deve categorizar notícia sobre enchentes e inundações como FLOOD")
    void shouldClassifyFlood() {
        Optional<NewsCategory> cat = classifier.classify(
                "Rio Guaíba ultrapassa cota de alerta e causa alagamento em bairros",
                "Inundação deixa famílias desabrigadas após transbordamento.",
                true
        );
        assertTrue(cat.isPresent());
        assertEquals(NewsCategory.FLOOD, cat.get());
    }

    @Test
    @DisplayName("Deve categorizar notícia sobre chuvas fortes e tempestades como RAIN")
    void shouldClassifyRain() {
        Optional<NewsCategory> cat = classifier.classify(
                "Inmet emite alerta meteorológico para temporal com queda de granizo",
                "Precipitação intensa e ventos fortes atingem o Sul.",
                false
        );
        assertTrue(cat.isPresent());
        assertEquals(NewsCategory.RAIN, cat.get());
    }

    @Test
    @DisplayName("Deve categorizar notícia sobre surto de dengue como DENGUE")
    void shouldClassifyDengue() {
        Optional<NewsCategory> cat = classifier.classify(
                "Campanha contra a dengue intensifica aplicação de fumacê nas ruas",
                "Casos de infecção transmitida pelo mosquito Aedes aegypti crescem.",
                false
        );
        assertTrue(cat.isPresent());
        assertEquals(NewsCategory.DENGUE, cat.get());
    }

    @Test
    @DisplayName("Deve categorizar seca e emergência climática como GENERAL")
    void shouldClassifyGeneral() {
        Optional<NewsCategory> cat = classifier.classify(
                "Estiagem prolongada reduz reservatórios a níveis críticos",
                "Defesa Civil declara situação de emergência climática devido à seca severa.",
                false
        );
        assertTrue(cat.isPresent());
        assertEquals(NewsCategory.GENERAL, cat.get());
    }

    @Test
    @DisplayName("Deve extrair cidade e estado corretamente de menções explícitas")
    void shouldExtractLocation() {
        NewsClassifier.LocationInfo loc = classifier.extractLocation(
                "Defesa Civil de Porto Alegre alerta para cheias no Rio Grande do Sul",
                "Ocorrências no estado do RS se intensificam."
        );
        assertEquals("Porto Alegre", loc.municipio());
        assertEquals("RS", loc.estado());
    }

    @Test
    @DisplayName("Deve limpar tags HTML e criar resumo conciso em linguagem cidadã")
    void shouldCleanAndSummarize() {
        String html = "<p>A <strong>Defesa Civil</strong> emitiu novo alerta nesta manhã.&nbsp;O rio subiu rapidamente.</p>";
        String summary = classifier.cleanAndSummarize(html, 150);
        assertFalse(summary.contains("<p>"));
        assertFalse(summary.contains("&nbsp;"));
        assertTrue(summary.contains("Defesa Civil"));
    }
}
