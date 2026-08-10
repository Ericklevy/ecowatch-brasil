package br.com.ecowatch.alert_service.infrastructure.api;

import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import br.com.ecowatch.alert_service.infrastructure.cache.WeatherCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
public class CacheController {

    private final WeatherCacheService cacheService;

    @GetMapping("/{municipio}")
    public ResponseEntity<WeatherReading> getCachedReading(@PathVariable String municipio) {
        Optional<WeatherReading> reading = cacheService.getLastReading(municipio);
        return reading.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}