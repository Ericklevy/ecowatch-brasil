package br.com.ecowatch.read_model_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI readModelOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Read Model (Dashboard) API")
                        .description("API otimizada para servir dados consolidados e em cache para os dashboards do EcoWatch.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Equipe EcoWatch").email("contato@ecowatch.com.br")));
    }
}
