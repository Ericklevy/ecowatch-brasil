package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.NewsDTO;
import br.com.ecowatch.read_model_service.application.service.NewsService;
import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NewsService newsService;

    @InjectMocks
    private NewsController newsController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(newsController).build();
    }

    @Test
    @DisplayName("GET /api/v1/news deve retornar lista paginada de notícias com estrutura esperada")
    void shouldReturnPaginatedNews() throws Exception {
        NewsDTO dto = NewsDTO.builder()
                .id(1L)
                .titulo("Alerta de chuvas intensas no RS")
                .resumo("Defesa Civil alerta para temporais em Porto Alegre e região metropolitana.")
                .fonte("Agência Brasil")
                .url("https://agenciabrasil.ebc.com.br/meio-ambiente/noticia/2026-10/alerta-chuvas-rs")
                .dataHora(Instant.parse("2026-10-14T10:00:00Z"))
                .categoria(NewsCategory.RAIN)
                .municipio("Porto Alegre")
                .estado("RS")
                .build();

        PageImpl<NewsDTO> page = new PageImpl<>(List.of(dto));

        Mockito.when(newsService.getNews(eq(NewsCategory.RAIN), eq("RS"), eq("Porto Alegre"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/news")
                        .param("categoria", "RAIN")
                        .param("estado", "RS")
                        .param("municipio", "Porto Alegre")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Alerta de chuvas intensas no RS"))
                .andExpect(jsonPath("$.content[0].resumo").value("Defesa Civil alerta para temporais em Porto Alegre e região metropolitana."))
                .andExpect(jsonPath("$.content[0].fonte").value("Agência Brasil"))
                .andExpect(jsonPath("$.content[0].url").value("https://agenciabrasil.ebc.com.br/meio-ambiente/noticia/2026-10/alerta-chuvas-rs"))
                .andExpect(jsonPath("$.content[0].categoria").value("RAIN"))
                .andExpect(jsonPath("$.content[0].municipio").value("Porto Alegre"))
                .andExpect(jsonPath("$.content[0].estado").value("RS"))
                .andExpect(jsonPath("$.content[0].dataHora").value("2026-10-14T10:00:00Z"));
    }

    @Test
    @DisplayName("POST /api/v1/news/sync deve disparar sincronização sob demanda e retornar contagem")
    void shouldTriggerSync() throws Exception {
        Mockito.when(newsService.syncNews()).thenReturn(8);

        mockMvc.perform(post("/api/v1/news/sync")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.novasNoticiasImportadas").value(8));
    }
}
