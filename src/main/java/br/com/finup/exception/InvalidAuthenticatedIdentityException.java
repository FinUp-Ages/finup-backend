package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Identidade autenticada presente, porem com um atributo que nao cabe no registro local.
 *
 * <p>400 e nao 401: quem chamou se identificou, o que esta errado e o formato do dado. E 400 e nao
 * 500 porque o limite e conhecido de antemao — deixar passar so adiava a falha para o {@code
 * INSERT}, onde virava {@code DataIntegrityViolationException} e 500.
 */
public class InvalidAuthenticatedIdentityException extends BusinessException {

  public InvalidAuthenticatedIdentityException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
