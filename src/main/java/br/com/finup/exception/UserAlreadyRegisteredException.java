package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * A identidade do Cognito ja tem um registro local vinculado.
 *
 * <p>409 e nao 400: a requisicao esta bem formada, o conflito e com o estado atual do sistema —
 * esta identidade ja foi provisionada antes.
 */
public class UserAlreadyRegisteredException extends BusinessException {

  public UserAlreadyRegisteredException(String cognitoId) {
    super(
        "Ja existe um usuario cadastrado para esta identidade (%s)".formatted(cognitoId),
        HttpStatus.CONFLICT);
  }
}
