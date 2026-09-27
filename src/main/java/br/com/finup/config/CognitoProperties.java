package br.com.finup.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identificacao do User Pool e do App Client do AWS Cognito.
 *
 * <p>So dois valores, ambos do console do Cognito: o resto e derivado. A regiao e o prefixo do ID
 * do User Pool ({@code us-east-1_AbCdEf123} esta em {@code us-east-1}), e dela saem o issuer, o
 * endereco das chaves publicas (JWKS) e o endpoint da API do Cognito.
 *
 * <p>Nenhum dos dois e segredo — o App Client do mobile e publico, sem client secret. Mesmo assim
 * vem de variavel de ambiente, porque muda por ambiente.
 *
 * <p>{@code @Validated}: sem os dois valores a aplicacao nao sobe, com uma mensagem dizendo qual
 * propriedade falta — em vez de subir e responder 401 para todo mundo.
 */
@Validated
@ConfigurationProperties(prefix = "finup.cognito")
public record CognitoProperties(
    @NotBlank
        @Pattern(
            regexp = "[a-z]{2}(-[a-z]+)+-\\d_\\w+",
            message = "deve ser o ID do User Pool do Cognito, ex.: us-east-1_AbCdEf123")
        String userPoolId,
    @NotBlank String clientId) {

  public String region() {
    return userPoolId.substring(0, userPoolId.indexOf('_'));
  }

  /** Valor esperado no claim {@code iss} dos tokens emitidos por este User Pool. */
  public String issuerUri() {
    return "https://cognito-idp.%s.amazonaws.com/%s".formatted(region(), userPoolId);
  }

  /** Chaves publicas com que o Cognito assina os tokens. */
  public String jwkSetUri() {
    return issuerUri() + "/.well-known/jwks.json";
  }

  /** Endpoint da API do Cognito na regiao do User Pool (usado pelo {@code GetUser}). */
  public String apiEndpoint() {
    return "https://cognito-idp.%s.amazonaws.com".formatted(region());
  }
}
