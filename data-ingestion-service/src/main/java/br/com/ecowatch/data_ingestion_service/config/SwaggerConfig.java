package br.com.ecowatch.data_ingestion_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI dataIngestionOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Data Ingestion API")
                        .description("API para monitoramento e controle do agendador de coleta climática.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Equipe EcoWatch").email("contato@ecowatch.com.br")));
    }
}
