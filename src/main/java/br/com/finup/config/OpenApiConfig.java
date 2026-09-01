package br.com.finup.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Identifica o contrato OpenAPI. Sem isto o Swagger publica "OpenAPI definition v1.0", e os
 * clientes gerados em finup-web e finup-mobile herdam esse nome.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI finUpOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("FinUp API")
                .description(
                    "API REST do FinUp. Contrato de integracao entre back-end, painel web e"
                        + " aplicativo mobile.")
                .version("v0.0.1")
                .contact(new Contact().name("AGES 2026/2 - FinUp"))
                .license(new License().name("Uso academico - PUCRS")));
  }
}
