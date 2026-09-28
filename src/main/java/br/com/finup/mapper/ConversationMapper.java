package br.com.finup.mapper;

import br.com.finup.dto.ConversationResponse;
import br.com.finup.model.Conversation;

/** Converte a entidade {@link Conversation} para o DTO de saída. */
public final class ConversationMapper {

  private ConversationMapper() {}

  public static ConversationResponse toResponse(Conversation conversation) {
    return new ConversationResponse(
        conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt());
  }
}
