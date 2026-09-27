package br.com.finup.security;

import br.com.finup.exception.InvalidAuthenticatedIdentityException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Identidade lida de headers, em vez do access token do Cognito. So para desenvolvimento.
 *
 * <p>Existe para quem precisa rodar a API sem ter um usuario no User Pool do Cognito. Os headers
 * {@code X-Mock-Cognito-*} nao sao parte do contrato da API; sao so um jeito de simular o que o
 * token do Cognito traria. Ativa com o profile {@code mock-auth} (ex.: {@code
 * SPRING_PROFILES_ACTIVE=dev,mock-auth}).
 *
 * <p>{@code sub} e {@code email} sao obrigatorios; {@code name} e opcional — no Cognito real,
 * alguns provedores (ex.: login com Apple) so mandam o nome no primeiro acesso.
 *
 * <p>Os tres sao validados em 255 caracteres, que e o limite das colunas correspondentes em {@code
 * users}. Sem isso, um header maior passava direto e so falhava no {@code INSERT}, virando 500.
 *
 * <p>{@code @Profile("mock-auth & !prod")}: mesmo que alguem ative {@code mock-auth} em producao,
 * esta classe nao e criada — e, como o {@link CognitoAuthenticatedIdentityResolver} tambem nao (ele
 * e {@code !mock-auth}), falta um bean de {@link AuthenticatedIdentityResolver} e a aplicacao nao
 * sobe. Mais seguro falhar a inicializacao do que aceitar qualquer header como identidade.
 */
@Component
@Profile("mock-auth & !prod")
public class MockAuthenticatedIdentityResolver implements AuthenticatedIdentityResolver {

  private static final String HEADER_COGNITO_SUB = "X-Mock-Cognito-Sub";
  private static final String HEADER_COGNITO_NAME = "X-Mock-Cognito-Name";
  private static final String HEADER_COGNITO_EMAIL = "X-Mock-Cognito-Email";

  /** Limite das colunas {@code cognito_id}, {@code name} e {@code email} em {@code users}. */
  private static final int MAX_ATTRIBUTE_LENGTH = 255;

  private final HttpServletRequest request;

  public MockAuthenticatedIdentityResolver(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public AuthenticatedIdentity resolveCurrent() {
    String cognitoId = request.getHeader(HEADER_COGNITO_SUB);
    String name = request.getHeader(HEADER_COGNITO_NAME);
    String email = request.getHeader(HEADER_COGNITO_EMAIL);

    if (!StringUtils.hasText(cognitoId) || !StringUtils.hasText(email)) {
      throw new MissingAuthenticatedIdentityException();
    }
    requireFitsInColumn(cognitoId, HEADER_COGNITO_SUB);
    requireFitsInColumn(name, HEADER_COGNITO_NAME);
    requireFitsInColumn(email, HEADER_COGNITO_EMAIL);

    return new AuthenticatedIdentity(cognitoId, name, email);
  }

  /** Os headers ja trazem e-mail e nome: e a mesma leitura de {@link #resolveCurrent()}. */
  @Override
  public AuthenticatedIdentity resolveCurrentWithAttributes() {
    return resolveCurrent();
  }

  private void requireFitsInColumn(String value, String header) {
    if (value != null && value.length() > MAX_ATTRIBUTE_LENGTH) {
      throw new InvalidAuthenticatedIdentityException(
          "%s deve ter no maximo %d caracteres".formatted(header, MAX_ATTRIBUTE_LENGTH));
    }
  }
}
