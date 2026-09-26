package br.com.finup.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Identifica o contrato OpenAPI. Sem isto o Swagger publica "OpenAPI definition v1.0", e os
 * clientes gerados em finup-web e finup-mobile herdam esse nome.
 *
 * <p>Tambem declara como a API se autentica, o que faz o Swagger UI mostrar o botao "Authorize":
 * com o Cognito, o access token (Bearer); com o profile {@code mock-auth}, os headers {@code
 * X-Mock-Cognito-*}. Vale para todos os endpoints, entao nenhum controller repete isso.
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_AUTH = "bearerAuth";
  private static final String MOCK_SUB = "mockCognitoSub";
  private static final String MOCK_EMAIL = "mockCognitoEmail";
  private static final String MOCK_NAME = "mockCognitoName";

  @Bean
  public OpenAPI finUpOpenAPI(Environment environment) {
    OpenAPI openApi =
        new OpenAPI()
            .info(
                new Info()
                    .title("FinUp API")
                    .description(
                        "API REST do FinUp. Contrato de integracao entre back-end, painel web e"
                            + " aplicativo mobile.")
                    .version("v0.0.1")
                    .contact(new Contact().name("AGES 2026/2 - FinUp"))
                    .license(new License().name("Uso academico - PUCRS")));

    if (environment.matchesProfiles("mock-auth")) {
      return openApi
          .components(
              new Components()
                  .addSecuritySchemes(
                      MOCK_SUB, mockHeader("X-Mock-Cognito-Sub", "sub da identidade. Obrigatorio."))
                  .addSecuritySchemes(
                      MOCK_EMAIL, mockHeader("X-Mock-Cognito-Email", "e-mail. Obrigatorio."))
                  .addSecuritySchemes(
                      MOCK_NAME, mockHeader("X-Mock-Cognito-Name", "nome. Opcional.")))
          .addSecurityItem(
              new SecurityRequirement().addList(MOCK_SUB).addList(MOCK_EMAIL).addList(MOCK_NAME));
    }
    return openApi
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token do AWS Cognito (nao o ID token).")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
  }

  private static SecurityScheme mockHeader(String header, String description) {
    return new SecurityScheme()
        .type(SecurityScheme.Type.APIKEY)
        .in(SecurityScheme.In.HEADER)
        .name(header)
        .description("Mock do Cognito (profile mock-auth): " + description);
  }
}
