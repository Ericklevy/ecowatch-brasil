package br.com.ecowatch.notification_service.application.service;

import br.com.ecowatch.notification_service.infrastructure.persistence.FcmTokenEntity;
import br.com.ecowatch.notification_service.infrastructure.persistence.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository repository;

    @Transactional
    public void registerToken(String municipio, String token) {
        // Verifica se o token já existe
        var optional = repository.findByToken(token);
        if (optional.isPresent()) {
            // Se existir, reativa e atualiza o município
            FcmTokenEntity entity = optional.get();
            entity.setAtivo(true);
            entity.setMunicipio(municipio);
            repository.save(entity);
            log.info("♻️ Token reativado para município: {}", municipio);
        } else {
            // Cria um novo
            FcmTokenEntity entity = new FcmTokenEntity(municipio, token);
            repository.save(entity);
            log.info("📲 Novo token registrado para município: {}", municipio);
        }
    }

    @Transactional
    public void unregisterToken(String token) {
        repository.deleteByToken(token);
        log.info("🗑️ Token removido: {}", token);
    }

    public List<String> getTokensByMunicipio(String municipio) {
        return repository.findByMunicipioAndAtivoTrue(municipio)
                .stream()
                .map(FcmTokenEntity::getToken)
                .toList();
    }
}