package br.com.pedidos.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Pedidos Assíncronos")
                        .version("1.0.0")
                        .description("""
                            API REST para recebimento e
                            processamento assíncrono de pedidos.

                            Tecnologias:
                            - Java 21
                            - Spring Boot 4
                            - RabbitMQ
                            - Java Swing

                            Os pedidos são processados
                            assincronamente e os status
                            são mantidos em memória.
                            """));
    }
}