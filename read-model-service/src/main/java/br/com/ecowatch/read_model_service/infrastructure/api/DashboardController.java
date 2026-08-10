package br.com.ecowatch.read_model_service.infrastructure.api;

import br.com.ecowatch.read_model_service.application.dto.DashboardDTO;
import br.com.ecowatch.read_model_service.application.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/{municipio}")
    public ResponseEntity<DashboardDTO> getDashboard(@PathVariable String municipio) {
        var dashboard = dashboardService.getDashboard(municipio);
        return ResponseEntity.ok(dashboard);
    }
}