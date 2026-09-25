package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.finup.exception.InvalidAuthenticatedIdentityException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testa a leitura dos headers em si — o {@code UserControllerTest} mocka {@link
 * AuthenticatedIdentityResolver} inteiro, entao nunca exercita este codigo. E aqui que se confirma
 * que {@code X-Mock-Cognito-Name} e realmente opcional, e que {@code Sub}/{@code Email} continuam
 * obrigatorios.
 */
class MockAuthenticatedIdentityResolverTest {

  @Test
  @DisplayName("resolve identidade com os 3 headers presentes")
  void resolvesWithAllHeaders() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Mock-Cognito-Sub")).thenReturn("cognito-sub-123");
    when(request.getHeader("X-Mock-Cognito-Name")).thenReturn("Ana Souza");
    when(request.getHeader("X-Mock-Cognito-Email")).thenReturn("ana@exemplo.com");
    MockAuthenticatedIdentityResolver resolver = new MockAuthenticatedIdentityResolver(request);

    AuthenticatedIdentity identity = resolver.resolveCurrent();

    assertThat(identity.cognitoId()).isEqualTo("cognito-sub-123");
    assertThat(identity.name()).isEqualTo("Ana Souza");
    assertThat(identity.email()).isEqualTo("ana@exemplo.com");
  }

  @Test
  @DisplayName("resolve identidade sem o header de nome, como no login com Apple")
  void resolvesWithoutNameHeader() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Mock-Cognito-Sub")).thenReturn("cognito-sub-123");
    when(request.getHeader("X-Mock-Cognito-Name")).thenReturn(null);
    when(request.getHeader("X-Mock-Cognito-Email")).thenReturn("ana@exemplo.com");
    MockAuthenticatedIdentityResolver resolver = new MockAuthenticatedIdentityResolver(request);

    AuthenticatedIdentity identity = resolver.resolveCurrent();

    assertThat(identity.name()).isNull();
  }

  @Test
  @DisplayName("sem o header de sub lanca MissingAuthenticatedIdentityException")
  void throwsWhenSubHeaderIsMissing() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Mock-Cognito-Sub")).thenReturn(null);
    when(request.getHeader("X-Mock-Cognito-Email")).thenReturn("ana@exemplo.com");
    MockAuthenticatedIdentityResolver resolver = new MockAuthenticatedIdentityResolver(request);

    assertThatThrownBy(resolver::resolveCurrent)
        .isInstanceOf(MissingAuthenticatedIdentityException.class);
  }

  @Test
  @DisplayName("sem o header de email lanca MissingAuthenticatedIdentityException")
  void throwsWhenEmailHeaderIsMissing() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Mock-Cognito-Sub")).thenReturn("cognito-sub-123");
    when(request.getHeader("X-Mock-Cognito-Email")).thenReturn(null);
    MockAuthenticatedIdentityResolver resolver = new MockAuthenticatedIdentityResolver(request);

    assertThatThrownBy(resolver::resolveCurrent)
        .isInstanceOf(MissingAuthenticatedIdentityException.class);
  }

  @Test
  @DisplayName("nome acima de 255 caracteres lanca InvalidAuthenticatedIdentityException")
  void throwsWhenNameExceedsColumnLength() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Mock-Cognito-Sub")).thenReturn("cognito-sub-123");
    when(request.getHeader("X-Mock-Cognito-Name")).thenReturn("a".repeat(256));
    when(request.getHeader("X-Mock-Cognito-Email")).thenReturn("ana@exemplo.com");
    MockAuthenticatedIdentityResolver resolver = new MockAuthenticatedIdentityResolver(request);

    assertThatThrownBy(resolver::resolveCurrent)
        .isInstanceOf(InvalidAuthenticatedIdentityException.class)
        .hasMessageContaining("X-Mock-Cognito-Name");
  }

  @Test
  @DisplayName("nome com exatamente 255 caracteres continua valendo")
  void acceptsNameAtColumnLimit() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Mock-Cognito-Sub")).thenReturn("cognito-sub-123");
    when(request.getHeader("X-Mock-Cognito-Name")).thenReturn("a".repeat(255));
    when(request.getHeader("X-Mock-Cognito-Email")).thenReturn("ana@exemplo.com");
    MockAuthenticatedIdentityResolver resolver = new MockAuthenticatedIdentityResolver(request);

    assertThat(resolver.resolveCurrent().name()).hasSize(255);
  }

  @Test
  @DisplayName("header de sub em branco conta como ausente")
  void treatsBlankSubAsMissing() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Mock-Cognito-Sub")).thenReturn("   ");
    when(request.getHeader("X-Mock-Cognito-Email")).thenReturn("ana@exemplo.com");
    MockAuthenticatedIdentityResolver resolver = new MockAuthenticatedIdentityResolver(request);

    assertThatThrownBy(resolver::resolveCurrent)
        .isInstanceOf(MissingAuthenticatedIdentityException.class);
  }
}
