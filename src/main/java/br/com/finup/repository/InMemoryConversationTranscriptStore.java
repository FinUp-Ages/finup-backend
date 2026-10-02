package br.com.finup.repository;

import br.com.finup.model.ChatMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transcripts em memoria, para desenvolvimento e testes sem bucket. O historico se perde ao
 * reiniciar a aplicacao — nunca use onde a conversa precise sobreviver.
 */
public class InMemoryConversationTranscriptStore implements ConversationTranscriptStore {

  private final Map<String, List<ChatMessage>> transcripts = new ConcurrentHashMap<>();

  @Override
  public List<ChatMessage> read(UUID userId, UUID conversationId) {
    return transcripts.getOrDefault(key(userId, conversationId), List.of());
  }

  @Override
  public void write(UUID userId, UUID conversationId, List<ChatMessage> messages) {
    transcripts.put(key(userId, conversationId), List.copyOf(messages));
  }

  private static String key(UUID userId, UUID conversationId) {
    return userId + "/" + conversationId;
  }
}
