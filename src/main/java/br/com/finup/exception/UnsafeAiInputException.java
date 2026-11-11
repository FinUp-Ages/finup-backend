package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Lancada quando o guardrail do Bedrock bloqueia texto do usuario (prompt injection ou jailbreak),
 * na mensagem do assistente ou num dado que vai para o prompt, como o nome de categoria. A mensagem
 * e fixa e nao repete o texto bloqueado.
 */
public class UnsafeAiInputException extends BusinessException {

  public UnsafeAiInputException() {
    this("Nao posso processar essa mensagem. Tente algo como \"gastei 20 reais no mercado\".");
  }

  public UnsafeAiInputException(String message) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
