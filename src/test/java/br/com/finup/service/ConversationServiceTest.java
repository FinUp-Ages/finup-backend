package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.finup.dto.ConversationResponse;
import br.com.finup.model.Conversation;
import br.com.finup.model.User;
import br.com.finup.repository.ConversationRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Testes unitarios de service, sem contexto Spring e sem banco: os repositorios sao dubles. */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock private ConversationRepository conversationRepository;

  @Mock private UserService userService;

  private ConversationService conversationService;

  @BeforeEach
  void setUp() {
    conversationService = new ConversationService(conversationRepository, userService);
  }

  @Test
  @DisplayName("findHistory devolve as conversas do usuario autenticado na ordem do repositorio")
  void findHistoryReturnsUserConversationsInRepositoryOrder() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub", "Ana Souza", "ana@example.com");
    User user = User.createFromCognitoIdentity("cognito-sub", "Ana Souza", "ana@example.com");
    Conversation newer = Conversation.startForUser(user, "Mais recente");
    Conversation older = Conversation.startForUser(user, "Mais antiga");
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(conversationRepository.findByUserOrderByUpdatedAtDesc(user))
        .thenReturn(List.of(newer, older));

    List<ConversationResponse> history = conversationService.findHistory(identity);

    assertThat(history)
        .extracting(ConversationResponse::title)
        .containsExactly("Mais recente", "Mais antiga");
  }
}
