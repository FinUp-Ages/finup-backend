package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * E-mail ja em uso por outro usuario.
 *
 * <p>409 e nao 400: o corpo da requisicao esta bem formado, o conflito e com o estado atual do
 * sistema. Erro de formato e problema do {@code @Valid}, no DTO.
 */
public class EmailAlreadyRegisteredException extends BusinessException {

  public EmailAlreadyRegisteredException(String email) {
    super("Ja existe um usuario cadastrado com o e-mail %s".formatted(email), HttpStatus.CONFLICT);
  }
}
