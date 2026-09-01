package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Excecao de regra de negocio. O service lanca, o {@link ApiExceptionHandler} traduz para HTTP.
 *
 * <p>O service nao conhece HTTP: quem escolhe o status e o handler, a partir do que a excecao
 * carrega. Prefira subclasses especificas a lancar esta diretamente.
 */
public class BusinessException extends RuntimeException {

  private final HttpStatus status;

  public BusinessException(String message) {
    this(message, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public BusinessException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
