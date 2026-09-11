package br.com.finup.security;

import br.com.finup.exception.MissingAuthenticatedIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Implementacao temporaria: le a identidade de headers em vez de validar um JWT do Cognito.
 *
 * <p>Existe para que o fluxo de cadastro rode de ponta a ponta sem depender do provisionamento do
 * Cognito (User Pool, App Client) — que ainda nao foi confirmado com o time. Os headers {@code
 * X-Mock-Cognito-*} nao sao parte do contrato final da API; sao so um jeito de simular, em
 * desenvolvimento, o que o Spring Security colocaria no {@code SecurityContext} automaticamente.
 *
 * <p><strong>Substituir quando o Cognito entrar:</strong> apague esta classe e implemente {@link
 * AuthenticatedIdentityResolver} lendo o {@code Jwt} do {@code SecurityContext} (via Spring
 * Security OAuth2 Resource Server). Nem o service nem o controller mudam — e esse o motivo de a
 * interface existir.
 */
@Component
public class MockAuthenticatedIdentityResolver implements AuthenticatedIdentityResolver {

  private static final String HEADER_COGNITO_SUB = "X-Mock-Cognito-Sub";
  private static final String HEADER_COGNITO_NAME = "X-Mock-Cognito-Name";
  private static final String HEADER_COGNITO_EMAIL = "X-Mock-Cognito-Email";

  private final HttpServletRequest request;

  public MockAuthenticatedIdentityResolver(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public AuthenticatedIdentity resolveCurrent() {
    String cognitoId = request.getHeader(HEADER_COGNITO_SUB);
    String name = request.getHeader(HEADER_COGNITO_NAME);
    String email = request.getHeader(HEADER_COGNITO_EMAIL);

    if (!StringUtils.hasText(cognitoId)
        || !StringUtils.hasText(name)
        || !StringUtils.hasText(email)) {
      throw new MissingAuthenticatedIdentityException();
    }

    return new AuthenticatedIdentity(cognitoId, name, email);
  }
}
