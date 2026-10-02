package br.com.finup.mapper;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.dto.ConversationResponse;
import br.com.finup.model.ChatMessage;
import br.com.finup.model.Conversation;

/** Converte a entidade {@link Conversation} para o DTO de saída. */
public final class ConversationMapper {

  private ConversationMapper() {}

  public static ConversationMessageResponse toResponse(ChatMessage message) {
    return new ConversationMessageResponse(
        message.role(), message.text(), message.action(), message.createdAt());
  }

  public static ConversationResponse toResponse(Conversation conversation) {
    return new ConversationResponse(
        conversation.getId(),
        conversation.getTitle(),
        conversation.getUpdatedAt(),
        conversation.getMessageCount());
  }
}
