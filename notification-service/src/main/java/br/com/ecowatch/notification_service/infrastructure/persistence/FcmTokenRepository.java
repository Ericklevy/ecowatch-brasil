package br.com.ecowatch.notification_service.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmTokenEntity, Long> {

    // Busca tokens ativos por município
    List<FcmTokenEntity> findByMunicipioAndAtivoTrue(String municipio);

    // Busca um token específico (para remover ou desativar)
    Optional<FcmTokenEntity> findByToken(String token);

    // Desativa um token (quando o usuário desinstala o app ou faz logout)
    void deleteByToken(String token);
}