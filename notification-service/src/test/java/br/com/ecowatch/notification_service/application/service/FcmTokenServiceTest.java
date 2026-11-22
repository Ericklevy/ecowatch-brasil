package br.com.ecowatch.notification_service.application.service;

import br.com.ecowatch.notification_service.infrastructure.persistence.FcmTokenEntity;
import br.com.ecowatch.notification_service.infrastructure.persistence.FcmTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {

    @Mock
    private FcmTokenRepository repository;

    @InjectMocks
    private FcmTokenService tokenService;

    @Test
    void shouldRegisterNewTokenWhenNotExists() {
        when(repository.findByToken("token_abc_123")).thenReturn(Optional.empty());

        tokenService.registerToken("Recife", "token_abc_123");

        ArgumentCaptor<FcmTokenEntity> captor = ArgumentCaptor.forClass(FcmTokenEntity.class);
        verify(repository).save(captor.capture());

        FcmTokenEntity saved = captor.getValue();
        assertEquals("Recife", saved.getMunicipio());
        assertEquals("token_abc_123", saved.getToken());
        assertTrue(saved.getAtivo());
    }

    @Test
    void shouldReactivateAndChangeCityWhenTokenAlreadyExists() {
        FcmTokenEntity existing = new FcmTokenEntity("Salvador", "token_xyz_456");
        existing.setAtivo(false);

        when(repository.findByToken("token_xyz_456")).thenReturn(Optional.of(existing));

        tokenService.registerToken("Fortaleza", "token_xyz_456");

        verify(repository).save(existing);
        assertEquals("Fortaleza", existing.getMunicipio());
        assertTrue(existing.getAtivo());
    }

    @Test
    void shouldUnregisterToken() {
        tokenService.unregisterToken("token_remover");

        verify(repository, times(1)).deleteByToken("token_remover");
    }

    @Test
    void shouldGetTokensByMunicipio() {
        FcmTokenEntity t1 = new FcmTokenEntity("Manaus", "tok1");
        FcmTokenEntity t2 = new FcmTokenEntity("Manaus", "tok2");

        when(repository.findByMunicipioAndAtivoTrue("Manaus")).thenReturn(List.of(t1, t2));

        List<String> tokens = tokenService.getTokensByMunicipio("Manaus");

        assertEquals(2, tokens.size());
        assertTrue(tokens.contains("tok1"));
        assertTrue(tokens.contains("tok2"));
    }
}
