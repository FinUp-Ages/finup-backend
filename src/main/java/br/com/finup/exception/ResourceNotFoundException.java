package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Recurso inexistente. Vira 404 com corpo no formato padrao da API. */
public class ResourceNotFoundException extends BusinessException {

  public ResourceNotFoundException(String recurso, Object id) {
    super("%s nao encontrado: %s".formatted(recurso, id), HttpStatus.NOT_FOUND);
  }
}
