package br.com.finup.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Validacao do access token do Cognito.
 *
 * <p>O decoder e montado a partir do JWKS, e nao de {@code spring.security...issuer-uri}: assim a
 * aplicacao nao consulta a AWS na inicializacao — as chaves so sao buscadas (e ficam em cache) no
 * primeiro token recebido. Os testes sobem o contexto sem rede por causa disso.
 *
 * <p>{@code @Profile("!mock-auth")}: com o mock de identidade ativo nao ha token para validar, e o
 * Cognito nao precisa estar configurado.
 */
@Configuration
@Profile("!mock-auth")
@EnableConfigurationProperties(CognitoProperties.class)
public class CognitoConfig {

  @Bean
  public JwtDecoder jwtDecoder(CognitoProperties cognito) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(cognito.jwkSetUri()).build();
    decoder.setJwtValidator(accessTokenValidator(cognito));
    return decoder;
  }

  /**
   * Alem de assinatura e validade (feitas pelo decoder), o token precisa:
   *
   * <ul>
   *   <li>ter sido emitido por este User Pool ({@code iss});
   *   <li>ser um access token ({@code token_use=access}) — o ID token tambem e assinado pelo mesmo
   *       pool, mas nao e credencial de acesso a API;
   *   <li>ter sido emitido para o nosso App Client ({@code client_id}). O access token do Cognito
   *       nao tem {@code aud}, por isso a checagem padrao de audience nao serve aqui.
   * </ul>
   */
  static OAuth2TokenValidator<Jwt> accessTokenValidator(CognitoProperties cognito) {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(cognito.issuerUri()),
        new JwtClaimValidator<String>("token_use", "access"::equals),
        new JwtClaimValidator<String>("client_id", cognito.clientId()::equals));
  }
}
