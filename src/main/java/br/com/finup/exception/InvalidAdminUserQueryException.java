package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Parametro de consulta de {@code GET /api/v1/admin/users} fora do que o contrato aceita. */
public class InvalidAdminUserQueryException extends BusinessException {

  public InvalidAdminUserQueryException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
