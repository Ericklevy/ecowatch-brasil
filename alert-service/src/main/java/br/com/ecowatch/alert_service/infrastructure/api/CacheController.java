package br.com.ecowatch.alert_service.infrastructure.api;

import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.infrastructure.cache.WeatherCacheService;
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

import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Tag(name = "Cache Climático", description = "Endpoints para consultar a última leitura armazenada no Redis por cidade.")
public class CacheController {

    private final WeatherCacheService cacheService;

    @Operation(summary = "Obter última leitura", description = "Busca a última leitura de clima de um município diretamente do cache Redis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Leitura encontrada no cache"),
            @ApiResponse(responseCode = "404", description = "Leitura não encontrada no cache")
    })
    @GetMapping("/{municipio}")
    public ResponseEntity<WeatherReading> getCachedReading(
            @Parameter(description = "Nome do município (ex: Brasilia)", required = true)
            @PathVariable String municipio) {
        Optional<WeatherReading> reading = cacheService.getLastReading(municipio);
        return reading.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}