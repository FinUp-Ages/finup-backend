package br.com.finup.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * O que o decoder aceita alem da assinatura: issuer do nosso User Pool, {@code token_use=access} e
 * {@code client_id} do nosso App Client. A assinatura em si e responsabilidade do Nimbus e nao e
 * retestada aqui.
 */
class CognitoConfigTest {

  private final CognitoProperties cognito =
      new CognitoProperties("us-east-1_TestPool", "test-client");
  private final OAuth2TokenValidator<Jwt> validator = CognitoConfig.accessTokenValidator(cognito);

  @Test
  @DisplayName("regiao, issuer e JWKS saem do ID do User Pool")
  void derivesEndpointsFromUserPoolId() {
    assertThat(cognito.region()).isEqualTo("us-east-1");
    assertThat(cognito.issuerUri())
        .isEqualTo("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TestPool");
    assertThat(cognito.jwkSetUri())
        .isEqualTo(
            "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TestPool/.well-known/jwks.json");
    assertThat(cognito.apiEndpoint()).isEqualTo("https://cognito-idp.us-east-1.amazonaws.com");
  }

  @Test
  @DisplayName("access token do nosso pool e do nosso app client e aceito")
  void acceptsAccessTokenFromOurPoolAndClient() {
    assertThat(validator.validate(accessToken().build()).hasErrors()).isFalse();
  }

  @Test
  @DisplayName("ID token e recusado, mesmo assinado pelo mesmo pool")
  void rejectsIdToken() {
    Jwt idToken = accessToken().claim("token_use", "id").build();

    assertThat(validator.validate(idToken).hasErrors()).isTrue();
  }

  @Test
  @DisplayName("token de outro app client e recusado")
  void rejectsOtherClient() {
    Jwt token = accessToken().claim("client_id", "outro-client").build();

    assertThat(validator.validate(token).hasErrors()).isTrue();
  }

  @Test
  @DisplayName("token de outro User Pool e recusado")
  void rejectsOtherIssuer() {
    Jwt token =
        accessToken()
            .issuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_OutroPool")
            .build();

    assertThat(validator.validate(token).hasErrors()).isTrue();
  }

  @Test
  @DisplayName("token expirado e recusado")
  void rejectsExpiredToken() {
    Instant now = Instant.now();
    Jwt token =
        accessToken().issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build();

    assertThat(validator.validate(token).hasErrors()).isTrue();
  }

  @Test
  @DisplayName(
      "sem o ID do User Pool a aplicacao nao sobe, e a mensagem diz qual propriedade falta")
  void failsStartupWithoutUserPoolId() {
    new ApplicationContextRunner()
        .withUserConfiguration(CognitoConfig.class)
        .withPropertyValues("finup.cognito.client-id=test-client")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure()).hasStackTraceContaining("userPoolId");
            });
  }

  @Test
  @DisplayName("ID do User Pool fora do formato <regiao>_<id> impede a aplicacao de subir")
  void failsStartupWithMalformedUserPoolId() {
    new ApplicationContextRunner()
        .withUserConfiguration(CognitoConfig.class)
        .withPropertyValues(
            "finup.cognito.user-pool-id=TestPool", "finup.cognito.client-id=test-client")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("com a configuracao completa o decoder e criado sem acessar a AWS")
  void createsDecoderWithoutNetwork() {
    new ApplicationContextRunner()
        .withUserConfiguration(CognitoConfig.class)
        .withPropertyValues(
            "finup.cognito.user-pool-id=us-east-1_TestPool", "finup.cognito.client-id=test-client")
        .run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
  }

  private Jwt.Builder accessToken() {
    Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuer(cognito.issuerUri())
        .subject("cognito-sub-123")
        .claim("token_use", "access")
        .claim("client_id", "test-client")
        .issuedAt(now.minusSeconds(60))
        .expiresAt(now.plusSeconds(3600));
  }
}
