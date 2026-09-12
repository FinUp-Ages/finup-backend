package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Operacao nao permitida sobre o recurso. Vira 403 com corpo no formato padrao da API.
 *
 * <p>Use quando o recurso existe mas o usuario nao tem permissao para a operacao solicitada,
 * como tentar editar ou deletar uma categoria padrao do sistema.
 */
public class ForbiddenOperationException extends BusinessException {

  public ForbiddenOperationException(String message) {
    super(message, HttpStatus.FORBIDDEN);
  }
}