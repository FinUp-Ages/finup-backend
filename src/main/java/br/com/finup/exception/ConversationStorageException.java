package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** O armazenamento do historico (S3) falhou. O detalhe tecnico fica no log. */
public class ConversationStorageException extends BusinessException {

  public ConversationStorageException(Throwable cause) {
    super("Nao foi possivel acessar o historico da conversa agora.", HttpStatus.BAD_GATEWAY);
    initCause(cause);
  }
}
