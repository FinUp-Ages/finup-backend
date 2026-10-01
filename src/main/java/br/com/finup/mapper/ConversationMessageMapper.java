package br.com.finup.mapper;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.model.ConversationMessage;

/** Converte a entidade {@link ConversationMessage} para o DTO de saída. */
public final class ConversationMessageMapper {

  private ConversationMessageMapper() {}

  public static ConversationMessageResponse toResponse(ConversationMessage message) {
    return new ConversationMessageResponse(
        message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
  }
}
