package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Recurso inexistente. Vira 404 com corpo no formato padrao da API. */
public class ResourceNotFoundException extends BusinessException {

  /** Recebe a mensagem completa para respeitar o idioma e o genero do recurso. */
  public ResourceNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }
}
