package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Data de termino anterior a data de inicio da recorrencia. */
public class InvalidRecurrencePeriodException extends BusinessException {

  public InvalidRecurrencePeriodException() {
    super(
        "A data de termino da recorrencia nao pode ser anterior a data de inicio",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
