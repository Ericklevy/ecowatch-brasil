package br.com.ecowatch.notification_service.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "fcm_tokens")
@Getter
@Setter
@NoArgsConstructor
public class FcmTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = true)
    private Long usuarioId; // opcional: se tiver cadastro de usuários

    @Column(name = "municipio", nullable = false)
    private String municipio; // token associado a um município (ex: "Brasília")

    @Column(name = "token", nullable = false, unique = true)
    private String token; // token FCM do dispositivo

    @Column(name = "data_registro")
    private LocalDateTime dataRegistro;

    @Column(name = "ativo")
    private Boolean ativo = true;

    // Construtor para facilitar criação
    public FcmTokenEntity(String municipio, String token) {
        this.municipio = municipio;
        this.token = token;
        this.dataRegistro = LocalDateTime.now();
        this.ativo = true;
    }
}