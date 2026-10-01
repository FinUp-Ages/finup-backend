package br.com.finup.repository;

import br.com.finup.model.Conversation;
import br.com.finup.model.ConversationMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

  /**
   * Mensagens da conversa em ordem cronológica, que é a ordem de renderização do chat. O {@code id}
   * só desempata mensagens gravadas no mesmo instante, para a ordem não variar entre chamadas.
   */
  List<ConversationMessage> findByConversationOrderByCreatedAtAscIdAsc(Conversation conversation);
}
