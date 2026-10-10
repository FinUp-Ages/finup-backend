package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Falha ao falar com o provedor de IA (Bedrock) ou resposta inutilizavel dele. O detalhe tecnico
 * fica no log; a mensagem aqui e segura para o cliente.
 */
public class AiProviderException extends BusinessException {

  public AiProviderException(String message, HttpStatus status, Throwable cause) {
    super(message, status);
    initCause(cause);
  }
}
