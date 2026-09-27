package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Indicador de recorrencia e periodicidade contraditorios no cadastro de uma transacao. */
public class InvalidTransactionRecurrenceException extends BusinessException {

  public InvalidTransactionRecurrenceException(String message) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
