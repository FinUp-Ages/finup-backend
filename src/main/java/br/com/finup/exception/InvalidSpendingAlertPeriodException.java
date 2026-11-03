package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Lançada quando o parâmetro days for inválido (menor ou igual a zero, ou fora dos limites). */
public class InvalidSpendingAlertPeriodException extends BusinessException {

  public InvalidSpendingAlertPeriodException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
