package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Recurso inexistente. Vira 404 com corpo no formato padrao da API. */
public class ResourceNotFoundException extends BusinessException {

  public ResourceNotFoundException(String recurso, Object id) {
    super("%s nao encontrado: %s".formatted(recurso, id), HttpStatus.NOT_FOUND);
  }

  /**
   * Para os casos em que o identificador nao deve aparecer na mensagem (ex.: identidade do Cognito)
   * — a mensagem ja vem pronta, sem interpolar nenhum dado sensivel.
   */
  public ResourceNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
