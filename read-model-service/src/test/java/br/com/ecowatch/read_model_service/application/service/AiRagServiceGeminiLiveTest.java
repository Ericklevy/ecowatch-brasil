package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.RagSourceItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class AiRagServiceGeminiLiveTest {

    @Test
    void testLiveGeminiApiKey() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertNotNull(apiKey, "GEMINI_API_KEY deve estar configurada no ambiente para o teste de integração");

        AiRagService service = new AiRagService(null, null, null, null, null);
        ReflectionTestUtils.setField(service, "geminiApiKey", apiKey);
        ReflectionTestUtils.setField(service, "geminiModel", "gemini-flash-lite-latest");
        ReflectionTestUtils.setField(service, "embeddingModel", "gemini-embedding-001");

        String embedding = service.generateEmbedding("Teste de monitoramento ambiental de queimadas e chuvas no Brasil");
        assertNotNull(embedding, "O embedding gerado pelo Google Gemini não deve ser nulo!");
        int dims = embedding.split(",").length;
        assertEquals(768, dims, "A dimensionalidade deve ser exatamente 768 para compatibilidade com pgvector");
        assertTrue(embedding.startsWith("[") && embedding.endsWith("]"), "O vetor deve começar com [ e terminar com ] para o pgvector");

        // Testa geração de texto com Gemini
        RagSourceItem fakeNews = RagSourceItem.builder()
                .id(1L)
                .titulo("Defesa Civil alerta para chuvas e ventos intensos em Porto Alegre")
                .fonte("Agência Brasil")
                .url("https://agenciabrasil.ebc.com.br/meio-ambiente")
                .dataHora(java.time.Instant.now())
                .categoria(br.com.ecowatch.read_model_service.domain.model.NewsCategory.RAIN)
                .municipio("Porto Alegre")
                .estado("RS")
                .resumo("A Defesa Civil emitiu alerta severo para temporais na capital gaúcha com risco de alagamentos.")
                .similaridade(0.92)
                .build();

        String answer = ReflectionTestUtils.invokeMethod(
                service, "generateLlmAnswer", "Quais os alertas de chuva no Rio Grande do Sul?", java.util.List.of(fakeNews)
        );

        assertNotNull(answer);
        assertFalse(answer.isBlank());
        assertTrue(answer.toLowerCase().contains("porto alegre") || answer.toLowerCase().contains("defesa civil") || answer.toLowerCase().contains("chuva"),
                "A resposta deve citar dados do contexto!");
    }
}
