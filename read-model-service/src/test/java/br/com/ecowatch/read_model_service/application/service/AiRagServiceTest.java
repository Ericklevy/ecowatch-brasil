package br.com.ecowatch.read_model_service.application.service;

import br.com.ecowatch.read_model_service.application.dto.AiAskRequest;
import br.com.ecowatch.read_model_service.application.dto.RagSourceItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiRagServiceTest {

    @Test
    void detectaCapitalSemAcentoEComPontuacao() {
        assertEquals(List.of("Belém"), AiRagService.detectarCapitais("Vai chover em belem amanhã?", null));
        assertEquals(List.of("Porto Alegre"), AiRagService.detectarCapitais("Rios em Porto Alegre, RS", null));
        assertTrue(AiRagService.detectarCapitais("Como está o desmatamento?", null).isEmpty());
    }

    @Test
    void usaCidadeDaConversaAnterior() {
        var historico = List.of(
                new AiAskRequest.HistoricoItem("usuario", "Como está o ar em Manaus?"),
                new AiAskRequest.HistoricoItem("ia", "O ar em Manaus está moderado."));
        assertEquals(List.of("Manaus"), AiRagService.detectarCapitais("e amanhã, vai chover?", historico));
        // Cidade nova na pergunta tem prioridade
        assertEquals(List.of("Recife"), AiRagService.detectarCapitais("e em Recife?", historico));
    }

    @Test
    void periodoDasFontes() {
        var a = RagSourceItem.builder().dataHora(Instant.parse("2026-11-18T15:00:00Z")).build();
        var b = RagSourceItem.builder().dataHora(Instant.parse("2026-11-21T15:00:00Z")).build();
        assertEquals("18/11 a 21/11", AiRagService.periodo(List.of(b, a)));
        assertEquals("18/11", AiRagService.periodo(List.of(a)));
        assertNull(AiRagService.periodo(List.of()));
    }
}
