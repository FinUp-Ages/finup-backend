package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Operação rejeitada por conflito com o estado atual do recurso. Vira 409 com corpo no formato
 * padrão da API.
 *
 * <p>Use quando a ação é válida em si, mas não pode ser executada agora porque outro recurso
 * depende do alvo — por exemplo, tentar excluir uma categoria que ainda está vinculada a
 * transações.
 */
public class ConflictException extends BusinessException {

  public ConflictException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
