package br.com.ecowatch.alert_service.infrastructure.cache;

import br.com.ecowatch.alert_service.domain.model.WeatherReading;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "weather:";
    private static final Duration TTL = Duration.ofHours(3);



    public void saveLastReading(WeatherReading reading) {
        try {
            String key = KEY_PREFIX + reading.municipio().toLowerCase();
            String json = objectMapper.writeValueAsString(reading);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("💾 Leitura de {} salva no Redis", reading.municipio());
        } catch (Exception e) {
            log.error("Erro ao salvar no Redis: {}", e.getMessage());
        }
    }

    public Optional<WeatherReading> getLastReading(String municipio) {
        try {
            String key = KEY_PREFIX + municipio.toLowerCase();
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                WeatherReading reading = objectMapper.readValue(json, WeatherReading.class);
                return Optional.of(reading);
            }
        } catch (Exception e) {
            log.error("Erro ao buscar do Redis: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public void clearCache(String municipio) {
        String key = KEY_PREFIX + municipio.toLowerCase();
        redisTemplate.delete(key);
        log.info("🗑️ Cache de {} removido", municipio);
    }
}