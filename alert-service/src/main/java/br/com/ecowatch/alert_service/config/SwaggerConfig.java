package br.com.ecowatch.alert_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI alertServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Alert Service API")
                        .description("API para recebimento de leituras e verificação de cache do serviço de alertas.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Equipe EcoWatch").email("contato@ecowatch.com.br")));
    }
}
