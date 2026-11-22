package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.EmissoesDTO.*;
import br.com.ecowatch.read_model_service.application.service.EmissoesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EmissoesControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EmissoesService emissoesService;

    @InjectMocks
    private EmissoesController emissoesController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(emissoesController).build();
    }

    @Test
    @DisplayName("GET /api/emissoes/ranking deve retornar ranking de municípios emissores")
    void shouldReturnRankingMunicipios() throws Exception {
        RankingMunicipioItem item = RankingMunicipioItem.builder()
                .codigoIbge("1507904")
                .municipio("São Félix do Xingu")
                .uf("PA")
                .ano(2023)
                .gas("CH4")
                .emissaoTotal(184201.77)
                .setorPrincipal("Agropecuária")
                .percentualSetorPrincipal(79.6)
                .explicacaoSetor("Em PA, a maior parte vem de Agropecuária (79.6%) (pecuária bovina, fermentação entérica).")
                .quebraPorSetor(Map.of("Agropecuária", 146624.0, "Mudança de uso da terra", 37577.0))
                .build();

        when(emissoesService.getRanking(eq("CH4"), eq("PA"), any(), eq(10)))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/emissoes/ranking")
                        .param("gas", "CH4")
                        .param("uf", "PA")
                        .param("limite", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoIbge").value("1507904"))
                .andExpect(jsonPath("$[0].municipio").value("São Félix do Xingu"))
                .andExpect(jsonPath("$[0].uf").value("PA"))
                .andExpect(jsonPath("$[0].gas").value("CH4"))
                .andExpect(jsonPath("$[0].setorPrincipal").value("Agropecuária"))
                .andExpect(jsonPath("$[0].percentualSetorPrincipal").value(79.6));
    }

    @Test
    @DisplayName("GET /api/emissoes/estados deve retornar totais agregados por UF")
    void shouldReturnEstadosEmissoes() throws Exception {
        EmissaoEstadoItem item = EmissaoEstadoItem.builder()
                .uf("PA")
                .ano(2023)
                .gas("CO2e")
                .emissaoTotal(422313087.46)
                .setorPrincipal("Mudança de uso da terra")
                .percentualSetorPrincipal(78.5)
                .explicacaoSetor("Em PA, a maior parte vem de Mudança de uso da terra (desmatamento).")
                .build();

        when(emissoesService.getEstados(eq("CO2e"), any()))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/emissoes/estados")
                        .param("gas", "CO2e")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uf").value("PA"))
                .andExpect(jsonPath("$[0].gas").value("CO2e"))
                .andExpect(jsonPath("$[0].emissaoTotal").value(422313087.46))
                .andExpect(jsonPath("$[0].setorPrincipal").value("Mudança de uso da terra"));
    }

    @Test
    @DisplayName("GET /api/emissoes/municipio/{nome} deve retornar série histórica do município")
    void shouldReturnMunicipioDetalhes() throws Exception {
        SerieAnoDTO serie2023 = SerieAnoDTO.builder()
                .ano(2023)
                .emissaoTotal(35255923.92)
                .quebraPorSetor(Map.of("Mudança de uso da terra", 28000000.0, "Agropecuária", 7255923.92))
                .build();

        MunicipioDetalheDTO dto = MunicipioDetalheDTO.builder()
                .codigoIbge("1507904")
                .municipio("São Félix do Xingu")
                .uf("PA")
                .gas("CO2e")
                .setorDominante("Mudança de uso da terra")
                .explicacaoSetorDominante("Em PA, a maior parte vem de Mudança de uso da terra.")
                .serieHistorica(List.of(serie2023))
                .build();

        when(emissoesService.getMunicipioDetalhe(eq("São Félix do Xingu"), eq("CO2e")))
                .thenReturn(dto);

        mockMvc.perform(get("/api/emissoes/municipio/{nome}", "São Félix do Xingu")
                        .param("gas", "CO2e")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.municipio").value("São Félix do Xingu"))
                .andExpect(jsonPath("$.uf").value("PA"))
                .andExpect(jsonPath("$.setorDominante").value("Mudança de uso da terra"))
                .andExpect(jsonPath("$.serieHistorica[0].ano").value(2023));
    }

    @Test
    @DisplayName("GET /api/emissoes/fontes deve retornar fontes do Climate TRACE")
    void shouldReturnFontesEmissaoClimateTrace() throws Exception {
        FonteEmissaoDTO fonte = FonteEmissaoDTO.builder()
                .assetId(60951975L)
                .nome("Aterro Sanitário de Seropédica")
                .setor("waste")
                .subSetor("solid-waste-disposal")
                .setorRotulo("Resíduos Sólidos e Aterros")
                .latitude(-22.75)
                .longitude(-43.71)
                .gas("CH4")
                .emissaoAnualT(175163.14)
                .ano(2024)
                .credito("Climate TRACE (CC BY 4.0)")
                .build();

        when(emissoesService.getFontesEmissao(eq("CH4"), any(), eq(100)))
                .thenReturn(List.of(fonte));

        mockMvc.perform(get("/api/emissoes/fontes")
                        .param("gas", "CH4")
                        .param("limite", "100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assetId").value(60951975))
                .andExpect(jsonPath("$[0].nome").value("Aterro Sanitário de Seropédica"))
                .andExpect(jsonPath("$[0].gas").value("CH4"))
                .andExpect(jsonPath("$[0].setorRotulo").value("Resíduos Sólidos e Aterros"))
                .andExpect(jsonPath("$[0].credito").value("Climate TRACE (CC BY 4.0)"));
    }
}
