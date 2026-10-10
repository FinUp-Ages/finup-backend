package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.Conversation;
import br.com.finup.model.User;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ConversationRepositoryTest {

  @Autowired private ConversationRepository conversationRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("persiste todos os campos da conversa")
  void persistsConversationFields() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("conversation-sub", "Ana Souza", "ana@example.com"));

    Conversation saved =
        conversationRepository.saveAndFlush(Conversation.startForUser(user, "Conta de luz"));

    assertThat(conversationRepository.findById(saved.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.getUser().getId()).isEqualTo(user.getId());
              assertThat(found.getTitle()).isEqualTo("Conta de luz");
              assertThat(found.getCreatedAt()).isNotNull();
              assertThat(found.getUpdatedAt()).isNotNull();
            });
  }

  @Test
  @DisplayName(
      "findByUserOrderByUpdatedAtDesc devolve apenas as conversas do usuario, da mais recente para a mais antiga")
  void findByUserOrderByUpdatedAtDescFiltersAndOrders() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("owner-sub", "Ana Souza", "ana@example.com"));
    User otherUser =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("other-sub", "Carlos", "carlos@example.com"));
    insertConversation(user.getId(), "Mais antiga", Instant.now().minusSeconds(60));
    insertConversation(user.getId(), "Mais recente", Instant.now());
    insertConversation(otherUser.getId(), "De outro usuario", Instant.now());

    assertThat(conversationRepository.findByUserOrderByUpdatedAtDesc(user))
        .extracting(Conversation::getTitle)
        .containsExactly("Mais recente", "Mais antiga");
  }

  @Test
  @DisplayName("findByIdAndUser so devolve a conversa do dono; a variante com lock tambem")
  void findByIdAndUserRespectsOwnership() {
    User owner =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("own-sub", "Ana Souza", "own@example.com"));
    User other =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("oth-sub", "Carlos", "oth@example.com"));
    Conversation saved =
        conversationRepository.saveAndFlush(Conversation.startForUser(owner, "Minha"));

    assertThat(conversationRepository.findByIdAndUser(saved.getId(), owner)).isPresent();
    assertThat(conversationRepository.findByIdAndUser(saved.getId(), other)).isEmpty();
    assertThat(conversationRepository.findByIdAndUserForUpdate(saved.getId(), owner)).isPresent();
    assertThat(conversationRepository.findByIdAndUserForUpdate(saved.getId(), other)).isEmpty();
  }

  @Test
  @DisplayName("message_count comeca em zero, inclusive em linha inserida sem a coluna, e persiste")
  void messageCountDefaultsToZeroAndPersists() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("count-sub", "Ana Souza", "count@example.com"));
    insertConversation(user.getId(), "Inserida por SQL", Instant.now());
    Conversation viaSql = conversationRepository.findByUserOrderByUpdatedAtDesc(user).get(0);
    assertThat(viaSql.getMessageCount()).isZero();

    viaSql.recordMessages(2);
    conversationRepository.saveAndFlush(viaSql);

    assertThat(conversationRepository.findById(viaSql.getId()))
        .get()
        .extracting(Conversation::getMessageCount)
        .isEqualTo(2);
  }

  private void insertConversation(java.util.UUID userId, String title, Instant updatedAt) {
    jdbcTemplate.update(
        "INSERT INTO conversations (id, user_id, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        java.util.UUID.randomUUID(),
        userId,
        title,
        Timestamp.from(updatedAt),
        Timestamp.from(updatedAt));
  }
}
