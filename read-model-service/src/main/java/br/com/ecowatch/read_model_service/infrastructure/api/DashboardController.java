package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.DashboardDTO;
import br.com.ecowatch.read_model_service.application.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Endpoints de consulta rápida aos dados consolidados para o Frontend.")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Obter dados do Dashboard", description = "Retorna um objeto consolidado contendo os alertas recentes (últimos 10) e previsões climáticas futuras (próximos 3 dias) para a cidade especificada.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados do dashboard retornados com sucesso")
    })
    @GetMapping("/{municipio}")
    public ResponseEntity<DashboardDTO> getDashboard(
            @Parameter(description = "Nome do município a ser consultado (ex: Brasilia)", required = true)
            @PathVariable String municipio) {
        var dashboard = dashboardService.getDashboard(municipio);
        return ResponseEntity.ok(dashboard);
    }
}