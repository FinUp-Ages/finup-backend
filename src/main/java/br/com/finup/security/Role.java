package br.com.finup.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Papeis de acesso da API. Cada um corresponde a um grupo do User Pool do Cognito: quem esta no
 * grupo recebe o papel, lido do claim {@code cognito:groups} do access token.
 *
 * <p>O nome do grupo e comparado exatamente como esta no Cognito, que diferencia maiusculas de
 * minusculas. Grupo que nao esta nesta lista nao vira papel nenhum.
 */
public enum Role {
  ADMIN("admin");

  private static final String AUTHORITY_PREFIX = "ROLE_";

  private final String cognitoGroup;

  Role(String cognitoGroup) {
    this.cognitoGroup = cognitoGroup;
  }

  public String cognitoGroup() {
    return cognitoGroup;
  }

  /** No formato que {@code hasRole} espera: {@code ROLE_ADMIN}. */
  public GrantedAuthority authority() {
    return new SimpleGrantedAuthority(AUTHORITY_PREFIX + name());
  }
}
