package br.com.finup.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Identificacao do User Pool e dos App Clients do AWS Cognito.
 *
 * <p>Tudo vem do console do Cognito, e o resto e derivado. A regiao e o prefixo do ID do User Pool
 * ({@code us-east-1_AbCdEf123} esta em {@code us-east-1}), e dela saem o issuer, o endereco das
 * chaves publicas (JWKS) e o endpoint da API do Cognito.
 *
 * <p>{@code clientId} e o App Client do app mobile. {@code adminClientId} e o do painel web, no
 * mesmo User Pool; e opcional, para a aplicacao continuar subindo onde o painel ainda nao existe.
 *
 * <p>Nenhum deles e segredo — os App Clients sao publicos, sem client secret. Mesmo assim vem de
 * variavel de ambiente, porque muda por ambiente.
 *
 * <p>{@code @Validated}: sem os dois valores obrigatorios a aplicacao nao sobe, com uma mensagem
 * dizendo qual propriedade falta — em vez de subir e responder 401 para todo mundo.
 */
@Validated
@ConfigurationProperties(prefix = "finup.cognito")
public record CognitoProperties(
    @NotBlank
        @Pattern(
            regexp = "[a-z]{2}(-[a-z]+)+-\\d_\\w+",
            message = "deve ser o ID do User Pool do Cognito, ex.: us-east-1_AbCdEf123")
        String userPoolId,
    @NotBlank String clientId,
    String adminClientId) {

  /** App Clients cujos tokens a API aceita: o do app e, se configurado, o do painel. */
  public Set<String> allowedClientIds() {
    return Stream.of(clientId, adminClientId)
        .filter(StringUtils::hasText)
        .collect(Collectors.toUnmodifiableSet());
  }

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
