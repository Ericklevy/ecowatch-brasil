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

    @Test
    void detectaPerguntaSobreEmissoesEGases() {
        assertTrue(AiRagService.isEmissoesQuery("Qual cidade do Pará mais emite metano?"));
        assertTrue(AiRagService.isEmissoesQuery("Como está o metano em Manaus?"));
        assertTrue(AiRagService.isEmissoesQuery("Quais os maiores emissores de CO2 no Brasil?"));
        assertTrue(AiRagService.isEmissoesQuery("Quais dados do SEEG sobre gases de efeito estufa?"));
        assertFalse(AiRagService.isEmissoesQuery("Vai chover forte amanhã em Salvador?"));
        assertFalse(AiRagService.isEmissoesQuery("Qual o risco de dengue em Porto Alegre?"));
    }

    @Test
    void detectaGasCorreto() {
        assertEquals("CH4", AiRagService.detectarGas("Quem mais emite metano no Pará?"));
        assertEquals("CH4", AiRagService.detectarGas("Como está a concentração de CH4 em Cuiabá?"));
        assertEquals("CO2e", AiRagService.detectarGas("Qual a emissão de CO2 em São Paulo?"));
        assertEquals("CO2e", AiRagService.detectarGas("Emissões de carbono e efeito estufa"));
    }

    @Test
    void detectaEstadoCorreto() {
        assertEquals("PA", AiRagService.detectarEstado("Qual cidade do Pará mais emite metano?", null));
        assertEquals("AM", AiRagService.detectarEstado("Como estão as emissões no Amazonas?", null));
        assertEquals("MT", AiRagService.detectarEstado("Maiores queimadas em MT", null));
        assertEquals("SP", AiRagService.detectarEstado(null, "SP"));
        assertNull(AiRagService.detectarEstado("Como está o tempo hoje?", null));
    }

    @Test
    void montaContextoEmissoesComSeegEClimateTrace() {
        EmissoesService emissoesMock = org.mockito.Mockito.mock(EmissoesService.class);
        AiRagService service = new AiRagService(null, null, null, emissoesMock, null);

        var rankingItem = br.com.ecowatch.read_model_service.application.dto.EmissoesDTO.RankingMunicipioItem.builder()
                .codigoIbge("1507904")
                .municipio("São Félix do Xingu")
                .uf("PA")
                .gas("CH4")
                .emissaoTotal(184201.77)
                .setorPrincipal("Agropecuária")
                .percentualSetorPrincipal(79.6)
                .explicacaoSetor("pecuária bovina")
                .build();

        org.mockito.Mockito.when(emissoesMock.getRanking("CH4", "PA", null, 5))
                .thenReturn(List.of(rankingItem));

        var traceItem = br.com.ecowatch.read_model_service.application.dto.EmissoesDTO.FonteEmissaoDTO.builder()
                .assetId(101L)
                .nome("Aterro Metropolitano")
                .setor("waste")
                .setorRotulo("Resíduos Sólidos e Aterros")
                .emissaoAnualT(54000.0)
                .ano(2024)
                .gas("CH4")
                .credito("Climate TRACE (CC BY 4.0)")
                .build();

        org.mockito.Mockito.when(emissoesMock.getFontesEmissaoPorEstado("CH4", "PA", 5))
                .thenReturn(List.of(traceItem));

        String contexto = service.montarContextoEmissoes("Qual cidade do Pará mais emite metano?", List.of(), "PA");

        assertNotNull(contexto);
        assertTrue(contexto.contains("São Félix do Xingu"));
        assertTrue(contexto.contains("Agropecuária"));
        assertTrue(contexto.contains("Aterro Metropolitano"));
        assertTrue(contexto.contains("Climate TRACE"));
        assertTrue(contexto.contains("SEEG"));
    }
}
