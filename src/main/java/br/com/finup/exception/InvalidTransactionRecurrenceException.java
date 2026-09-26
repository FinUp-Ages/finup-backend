package br.com.finup.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionRecurrenceException extends BusinessException {

  public InvalidTransactionRecurrenceException() {
    super("Dados de recorrencia invalidos para a transacao", HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
