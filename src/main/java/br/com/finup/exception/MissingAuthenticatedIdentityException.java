package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Requisicao sem identidade autenticada resolvivel.
 *
 * <p>Quando o Cognito real entrar via Spring Security, isto corresponde a um token ausente ou
 * invalido — por isso 401, e nao 400: a requisicao esta bem formada, falta e quem a fez se
 * autenticar.
 */
public class MissingAuthenticatedIdentityException extends BusinessException {

  public MissingAuthenticatedIdentityException() {
    super("Identidade autenticada ausente ou incompleta", HttpStatus.UNAUTHORIZED);
  }
}
