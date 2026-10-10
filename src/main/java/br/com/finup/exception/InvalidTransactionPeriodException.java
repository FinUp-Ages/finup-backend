package br.com.finup.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionPeriodException extends BusinessException {

  public InvalidTransactionPeriodException() {
    super(
        "Periodo de transacoes invalido: from deve ser anterior ou igual a to.",
        HttpStatus.BAD_REQUEST);
  }
}
