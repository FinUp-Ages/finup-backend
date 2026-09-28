package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finup.exception.InvalidAuthenticatedIdentityException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CognitoAuthenticatedIdentityResolverTest {

  private final CognitoUserAttributesClient userAttributesClient =
      mock(CognitoUserAttributesClient.class);
  private final CognitoAuthenticatedIdentityResolver resolver =
      new CognitoAuthenticatedIdentityResolver(userAttributesClient);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("resolveCurrent le so o sub do token, sem consultar o Cognito")
  void resolveCurrentReadsOnlySubject() {
    authenticateWith("cognito-sub-123");

    AuthenticatedIdentity identity = resolver.resolveCurrent();

    assertThat(identity.cognitoId()).isEqualTo("cognito-sub-123");
    assertThat(identity.email()).isNull();
    assertThat(identity.name()).isNull();
    verifyNoInteractions(userAttributesClient);
  }

  @Test
  @DisplayName("sem token no contexto devolve 401")
  void missingTokenIsUnauthorized() {
    assertThatThrownBy(resolver::resolveCurrent)
        .isInstanceOf(MissingAuthenticatedIdentityException.class);
  }

  @Test
  @DisplayName("resolveCurrentWithAttributes junta o sub do token com e-mail e nome do Cognito")
  void resolveWithAttributesUsesCognito() {
    authenticateWith("cognito-sub-123");
    when(userAttributesClient.fetch("token-abc"))
        .thenReturn(
            new CognitoUserAttributesClient.UserAttributes(
                "cognito-sub-123", "ana@exemplo.com", "Ana Souza"));

    AuthenticatedIdentity identity = resolver.resolveCurrentWithAttributes();

    assertThat(identity)
        .isEqualTo(new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com"));
  }

  @Test
  @DisplayName("atributos de outra identidade sao recusados")
  void rejectsAttributesFromAnotherSubject() {
    authenticateWith("cognito-sub-123");
    when(userAttributesClient.fetch("token-abc"))
        .thenReturn(
            new CognitoUserAttributesClient.UserAttributes(
                "outro-sub", "ana@exemplo.com", "Ana Souza"));

    assertThatThrownBy(resolver::resolveCurrentWithAttributes)
        .isInstanceOf(MissingAuthenticatedIdentityException.class);
  }

  @Test
  @DisplayName("usuario do Cognito sem e-mail devolve 401")
  void rejectsIdentityWithoutEmail() {
    authenticateWith("cognito-sub-123");
    when(userAttributesClient.fetch("token-abc"))
        .thenReturn(new CognitoUserAttributesClient.UserAttributes("cognito-sub-123", null, null));

    assertThatThrownBy(resolver::resolveCurrentWithAttributes)
        .isInstanceOf(MissingAuthenticatedIdentityException.class);
  }

  @Test
  @DisplayName("nome maior que a coluna devolve 400, e nao 500 no INSERT")
  void rejectsNameLongerThanColumn() {
    authenticateWith("cognito-sub-123");
    when(userAttributesClient.fetch("token-abc"))
        .thenReturn(
            new CognitoUserAttributesClient.UserAttributes(
                "cognito-sub-123", "ana@exemplo.com", "a".repeat(256)));

    assertThatThrownBy(resolver::resolveCurrentWithAttributes)
        .isInstanceOf(InvalidAuthenticatedIdentityException.class);
  }

  private static void authenticateWith(String subject) {
    Instant now = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
