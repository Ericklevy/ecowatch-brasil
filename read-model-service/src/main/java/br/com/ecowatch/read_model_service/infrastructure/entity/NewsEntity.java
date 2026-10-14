package br.com.ecowatch.read_model_service.infrastructure.entity;

import br.com.ecowatch.read_model_service.domain.model.NewsCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "noticias_ambientais",
        indexes = {
                @Index(name = "idx_noticias_categoria", columnList = "categoria"),
                @Index(name = "idx_noticias_estado", columnList = "estado"),
                @Index(name = "idx_noticias_municipio", columnList = "municipio"),
                @Index(name = "idx_noticias_data_hora", columnList = "data_hora")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String resumo;

    @Column(nullable = false, length = 100)
    private String fonte;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsCategory categoria;

    @Column(length = 100)
    private String municipio;

    @Column(length = 2)
    private String estado;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    @PrePersist
    public void prePersist() {
        if (dataCriacao == null) {
            dataCriacao = Instant.now();
        }
    }
}
