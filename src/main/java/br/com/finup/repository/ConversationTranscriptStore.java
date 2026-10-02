package br.com.finup.repository;

import br.com.finup.model.ChatMessage;
import java.util.List;
import java.util.UUID;

/**
 * Onde fica o corpo das conversas do assistente. O banco guarda so o metadado; o texto vive aqui.
 *
 * <p>Mesmo papel de {@link FinUpScoreDataProvider}: {@code ConversationService} depende so desta
 * interface. Em producao e o S3 ({@link S3ConversationTranscriptStore}); sem bucket configurado, a
 * memoria ({@link InMemoryConversationTranscriptStore}).
 */
public interface ConversationTranscriptStore {

  /** Mensagens da conversa, em ordem. Vazio se a conversa ainda nao tem transcript. */
  List<ChatMessage> read(UUID userId, UUID conversationId);

  /** Substitui o transcript inteiro da conversa. */
  void write(UUID userId, UUID conversationId, List<ChatMessage> messages);
}
