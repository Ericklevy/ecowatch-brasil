package br.com.ecowatch.notification_service.infrastructure.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "defaultTo", "defesa.civil@ecowatch.com.br");
    }

    @Test
    void shouldSendEmailToSpecifiedRecipient() {
        emailService.sendAlertEmail("destinatario@teste.com", "Alerta Crítico", "Corpo do e-mail");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("destinatario@teste.com", sent.getTo()[0]);
        assertEquals("Alerta Crítico", sent.getSubject());
        assertEquals("Corpo do e-mail", sent.getText());
    }

    @Test
    void shouldFallbackToDefaultEmailWhenRecipientIsNull() {
        emailService.sendAlertEmail(null, "Alerta Inundação", "Nível do rio subindo");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("defesa.civil@ecowatch.com.br", sent.getTo()[0]);
    }

    @Test
    void shouldFallbackToDefaultEmailWhenRecipientIsBlank() {
        emailService.sendAlertEmail("   ", "Alerta Chuva", "Precipitação alta");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("defesa.civil@ecowatch.com.br", sent.getTo()[0]);
    }

    @Test
    void shouldHandleMailExceptionGracefullyWithoutThrowing() {
        doThrow(new MailSendException("SMTP timeout")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> {
            emailService.sendAlertEmail("teste@teste.com", "Assunto", "Mensagem");
        });
    }

    @Test
    void shouldSendEmailWithOverloadedMethod() {
        emailService.sendAlertEmail("Assunto Rápido", "Mensagem Rápida");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        assertEquals("defesa.civil@ecowatch.com.br", captor.getValue().getTo()[0]);
    }
}
