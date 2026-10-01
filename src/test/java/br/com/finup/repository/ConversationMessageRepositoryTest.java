package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.Conversation;
import br.com.finup.model.ConversationMessage;
import br.com.finup.model.MessageRole;
import br.com.finup.model.User;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ConversationMessageRepositoryTest {

  @Autowired private ConversationMessageRepository conversationMessageRepository;

  @Autowired private ConversationRepository conversationRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("persiste todos os campos da mensagem")
  void persistsMessageFields() {
    Conversation conversation = conversationOf("message-sub", "ana@example.com");

    ConversationMessage saved =
        conversationMessageRepository.saveAndFlush(
            ConversationMessage.of(conversation, MessageRole.USER, "Quanto gastei?"));

    assertThat(conversationMessageRepository.findById(saved.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.getConversation().getId()).isEqualTo(conversation.getId());
              assertThat(found.getRole()).isEqualTo(MessageRole.USER);
              assertThat(found.getContent()).isEqualTo("Quanto gastei?");
              assertThat(found.getCreatedAt()).isNotNull();
            });
  }

  @Test
  @DisplayName(
      "findByConversationOrderByCreatedAtAscIdAsc devolve apenas as mensagens da conversa, da mais antiga para a mais recente")
  void findByConversationFiltersAndOrdersChronologically() {
    Conversation conversation = conversationOf("owner-sub", "ana@example.com");
    Conversation otherConversation = conversationOf("other-sub", "carlos@example.com");
    Instant start = Instant.parse("2026-09-26T10:00:00Z");
    insertMessage(conversation.getId(), "ASSISTANT", "Segunda", start.plusSeconds(5));
    insertMessage(conversation.getId(), "USER", "Terceira", start.plusSeconds(60));
    insertMessage(conversation.getId(), "USER", "Primeira", start);
    insertMessage(otherConversation.getId(), "USER", "De outra conversa", start.plusSeconds(1));

    assertThat(
            conversationMessageRepository.findByConversationOrderByCreatedAtAscIdAsc(conversation))
        .extracting(ConversationMessage::getContent)
        .containsExactly("Primeira", "Segunda", "Terceira");
  }

  private Conversation conversationOf(String cognitoId, String email) {
    User user =
        userRepository.saveAndFlush(User.createFromCognitoIdentity(cognitoId, "Nome", email));
    return conversationRepository.saveAndFlush(Conversation.startForUser(user, "Conversa"));
  }

  private void insertMessage(UUID conversationId, String role, String content, Instant createdAt) {
    jdbcTemplate.update(
        "INSERT INTO conversation_messages (id, conversation_id, role, content, created_at)"
            + " VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        conversationId,
        role,
        content,
        Timestamp.from(createdAt));
  }
}
