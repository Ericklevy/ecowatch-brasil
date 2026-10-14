package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.NewsDTO;
import br.com.ecowatch.read_model_service.application.dto.PageResponse;
import br.com.ecowatch.read_model_service.application.service.NewsService;
import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Tag(name = "Notícias e Comunicados", description = "Endpoints de consulta a notícias e comunicados oficiais de desastres ambientais.")
public class NewsController {

    private final NewsService newsService;

    @Operation(
            summary = "Listar notícias ambientais",
            description = "Retorna notícias e comunicados oficiais de desastres ambientais (queimadas, enchentes, chuvas, dengue, desmatamento, secas) com paginação e filtros opcionais por categoria, estado e município."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista paginada de notícias retornada com sucesso")
    })
    @GetMapping
    public ResponseEntity<PageResponse<NewsDTO>> getNews(
            @Parameter(description = "Categoria da notícia: FIRE, FLOOD, RAIN, DENGUE, DEFORESTATION, GENERAL")
            @RequestParam(required = false) NewsCategory categoria,
            @Parameter(description = "Sigla do estado (UF de 2 letras, ex: SP, RS, DF)")
            @RequestParam(required = false) String estado,
            @Parameter(description = "Nome do município (ex: Porto Alegre, Brasília)")
            @RequestParam(required = false) String municipio,
            @Parameter(description = "Número da página (início em 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de registros por página (máx: 50)")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Direção da ordenação: desc ou asc (padrão: desc)")
            @RequestParam(defaultValue = "desc") String direction
    ) {
        int validSize = Math.min(Math.max(size, 1), 50);
        int validPage = Math.max(page, 0);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by(sortDirection, "dataHora"));

        Page<NewsDTO> result = newsService.getNews(categoria, estado, municipio, pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @Operation(
            summary = "Sincronizar notícias sob demanda",
            description = "Dispara a coleta imediata dos feeds RSS oficiais (Agência Brasil, G1 Meio Ambiente, Agência Gov), processa, classifica e persiste novas matérias."
    )
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> triggerSync() {
        int imported = newsService.syncNews();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "novasNoticiasImportadas", imported,
                "timestamp", java.time.Instant.now().toString()
        ));
    }

    @Operation(
            summary = "Limpar matérias irrelevantes do banco",
            description = "Varre o banco de dados e remove matérias que contenham termos de ruído (política, eleições, curiosidades) ou que não atendam às regras de desastres climáticos, reclassificando as remanescentes."
    )
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> triggerCleanup() {
        int purged = newsService.purgeIrrelevantNews();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "noticiasIrrelevantesRemovidas", purged,
                "timestamp", java.time.Instant.now().toString()
        ));
    }
}
