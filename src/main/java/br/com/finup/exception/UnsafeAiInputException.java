package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/**
 * Lancada quando o guardrail do Bedrock bloqueia a mensagem do usuario (prompt injection ou
 * jailbreak). A mensagem e fixa e nao repete o texto bloqueado.
 */
public class UnsafeAiInputException extends BusinessException {

  public UnsafeAiInputException() {
    super(
        "Nao posso processar essa mensagem. Tente algo como \"gastei 20 reais no mercado\".",
        HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
