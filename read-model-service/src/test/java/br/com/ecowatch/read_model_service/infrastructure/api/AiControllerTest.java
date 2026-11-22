package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.AiAskRequest;
import br.com.ecowatch.read_model_service.application.dto.AiAskResponse;
import br.com.ecowatch.read_model_service.application.dto.AiStatusResponse;
import br.com.ecowatch.read_model_service.application.dto.RagSourceItem;
import br.com.ecowatch.read_model_service.application.service.AiRagService;
import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiRagService aiRagService;

    @InjectMocks
    private AiController aiController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiController).build();
    }

    @Test
    @DisplayName("POST /api/v1/ai/ask deve responder pergunta do usuário com fontes RAG")
    void shouldAnswerUserQuestion() throws Exception {
        RagSourceItem source = RagSourceItem.builder()
                .id(1L)
                .titulo("Rio Uruguai em elevação atinge cota de alerta")
                .fonte("Agência Brasil")
                .url("https://agenciabrasil.ebc.com.br/noticia/rio-uruguai")
                .dataHora(Instant.now())
                .categoria(NewsCategory.FLOOD)
                .estado("RS")
                .similaridade(0.92)
                .build();

        AiAskResponse response = AiAskResponse.builder()
                .pergunta("Como está a situação dos rios no RS?")
                .resposta("O nível do Rio Uruguai está em elevação e atingiu a cota de alerta.")
                .fontes(List.of(source))
                .totalFontesEncontradas(1)
                .modelo("gemini-2.5-flash")
                .tempoRespostaMs(120)
                .build();

        Mockito.when(aiRagService.ask(any(AiAskRequest.class))).thenReturn(response);

        AiAskRequest request = AiAskRequest.builder()
                .pergunta("Como está a situação dos rios no RS?")
                .categoria(NewsCategory.FLOOD)
                .estado("RS")
                .build();

        mockMvc.perform(post("/api/v1/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pergunta").value("Como está a situação dos rios no RS?"))
                .andExpect(jsonPath("$.resposta").value("O nível do Rio Uruguai está em elevação e atingiu a cota de alerta."))
                .andExpect(jsonPath("$.totalFontesEncontradas").value(1))
                .andExpect(jsonPath("$.fontes[0].categoria").value("FLOOD"))
                .andExpect(jsonPath("$.fontes[0].estado").value("RS"));
    }

    @Test
    @DisplayName("GET /api/v1/ai/status deve retornar status da infraestrutura de IA")
    void shouldReturnAiStatus() throws Exception {
        AiStatusResponse statusResponse = AiStatusResponse.builder()
                .pgvectorAtivo(true)
                .totalNoticias(35)
                .totalEmbeddings(35)
                .geminiConfigurado(true)
                .modeloPadrao("gemini-2.5-flash")
                .build();

        Mockito.when(aiRagService.getStatus()).thenReturn(statusResponse);

        mockMvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pgvectorAtivo").value(true))
                .andExpect(jsonPath("$.totalNoticias").value(35))
                .andExpect(jsonPath("$.geminiConfigurado").value(true));
    }
}
