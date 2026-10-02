package br.com.finup.model;

import java.time.Instant;

/**
 * Uma mensagem do historico de uma conversa com o assistente. E o formato gravado no transcript do
 * S3, entao mudar campos aqui afeta objetos ja gravados: so acrescente campos opcionais.
 *
 * @param action acao executada pelo assistente (so nas mensagens do ASSISTANT)
 */
public record ChatMessage(Role role, String text, String action, Instant createdAt) {

  public enum Role {
    USER,
    ASSISTANT
  }
}
