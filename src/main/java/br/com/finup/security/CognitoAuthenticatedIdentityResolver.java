package br.com.finup.security;

import br.com.finup.exception.InvalidAuthenticatedIdentityException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Identidade a partir do access token do Cognito, ja validado pelo Spring Security (assinatura,
 * validade, issuer, {@code token_use} e {@code client_id} — veja {@code CognitoConfig}).
 *
 * <p>{@link #resolveCurrent()} so le o {@code sub} do token, sem rede. {@link
 * #resolveCurrentWithAttributes()} consulta o Cognito para trazer e-mail e nome, e so e usado no
 * cadastro.
 */
@Component
@Profile("!mock-auth")
public class CognitoAuthenticatedIdentityResolver implements AuthenticatedIdentityResolver {

  /** Limite das colunas {@code name} e {@code email} em {@code users}. */
  private static final int MAX_ATTRIBUTE_LENGTH = 255;

  private final CognitoUserAttributesClient userAttributesClient;

  public CognitoAuthenticatedIdentityResolver(CognitoUserAttributesClient userAttributesClient) {
    this.userAttributesClient = userAttributesClient;
  }

  @Override
  public AuthenticatedIdentity resolveCurrent() {
    return new AuthenticatedIdentity(currentToken().getSubject(), null, null);
  }

  /**
   * O {@code sub} devolvido pelo Cognito tem que bater com o do token: e a garantia de que os
   * atributos sao da mesma identidade que a requisicao autenticou.
   */
  @Override
  public AuthenticatedIdentity resolveCurrentWithAttributes() {
    Jwt token = currentToken();
    CognitoUserAttributesClient.UserAttributes attributes =
        userAttributesClient.fetch(token.getTokenValue());

    if (!token.getSubject().equals(attributes.sub()) || !StringUtils.hasText(attributes.email())) {
      throw new MissingAuthenticatedIdentityException();
    }
    requireFitsInColumn(attributes.email(), "email");
    requireFitsInColumn(attributes.name(), "name");

    return new AuthenticatedIdentity(token.getSubject(), attributes.name(), attributes.email());
  }

  private Jwt currentToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      return jwtAuthentication.getToken();
    }
    throw new MissingAuthenticatedIdentityException();
  }

  private void requireFitsInColumn(String value, String attribute) {
    if (value != null && value.length() > MAX_ATTRIBUTE_LENGTH) {
      throw new InvalidAuthenticatedIdentityException(
          "O atributo %s do Cognito deve ter no maximo %d caracteres"
              .formatted(attribute, MAX_ATTRIBUTE_LENGTH));
    }
  }
}
