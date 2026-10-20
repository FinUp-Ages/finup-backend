package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Parametro de consulta das metricas do dashboard fora do que o contrato aceita. */
public class InvalidMetricsParameterException extends BusinessException {

  public InvalidMetricsParameterException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
