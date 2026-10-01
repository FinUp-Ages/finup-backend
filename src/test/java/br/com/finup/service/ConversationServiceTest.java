package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.dto.ConversationResponse;
import br.com.finup.exception.ForbiddenOperationException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Conversation;
import br.com.finup.model.ConversationMessage;
import br.com.finup.model.MessageRole;
import br.com.finup.model.User;
import br.com.finup.repository.ConversationMessageRepository;
import br.com.finup.repository.ConversationRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Testes unitarios de service, sem contexto Spring e sem banco: os repositorios sao dubles. */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock private ConversationRepository conversationRepository;

  @Mock private ConversationMessageRepository conversationMessageRepository;

  @Mock private UserService userService;

  private ConversationService conversationService;

  @BeforeEach
  void setUp() {
    conversationService =
        new ConversationService(conversationRepository, conversationMessageRepository, userService);
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

  @Test
  @DisplayName("findMessages devolve as mensagens da conversa do usuario na ordem do repositorio")
  void findMessagesReturnsOwnConversationMessagesInRepositoryOrder() {
    User user = persistedUser("cognito-sub", "ana@example.com");
    AuthenticatedIdentity identity = identity(user);
    Conversation conversation = persistedConversation(user);
    ConversationMessage question =
        ConversationMessage.of(conversation, MessageRole.USER, "Quanto gastei?");
    ConversationMessage answer =
        ConversationMessage.of(conversation, MessageRole.ASSISTANT, "R$ 820,00");
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));
    when(conversationMessageRepository.findByConversationOrderByCreatedAtAscIdAsc(conversation))
        .thenReturn(List.of(question, answer));

    List<ConversationMessageResponse> messages =
        conversationService.findMessages(identity, conversation.getId());

    assertThat(messages)
        .extracting(ConversationMessageResponse::role, ConversationMessageResponse::content)
        .containsExactly(
            tuple(MessageRole.USER, "Quanto gastei?"), tuple(MessageRole.ASSISTANT, "R$ 820,00"));
  }

  @Test
  @DisplayName("findMessages de conversa inexistente lanca ResourceNotFoundException")
  void findMessagesOfMissingConversationThrowsNotFound() {
    User user = persistedUser("cognito-sub", "ana@example.com");
    AuthenticatedIdentity identity = identity(user);
    UUID missingId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(conversationRepository.findById(missingId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.findMessages(identity, missingId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Conversa nao encontrada: " + missingId);
    verify(conversationMessageRepository, never())
        .findByConversationOrderByCreatedAtAscIdAsc(any());
  }

  @Test
  @DisplayName(
      "findMessages de conversa de outro usuario lanca ForbiddenOperationException sem ler as mensagens")
  void findMessagesOfAnotherUsersConversationThrowsForbidden() {
    User user = persistedUser("cognito-sub", "ana@example.com");
    User owner = persistedUser("other-sub", "carlos@example.com");
    AuthenticatedIdentity identity = identity(user);
    Conversation conversation = persistedConversation(owner);
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> conversationService.findMessages(identity, conversation.getId()))
        .isInstanceOf(ForbiddenOperationException.class);
    verify(conversationMessageRepository, never())
        .findByConversationOrderByCreatedAtAscIdAsc(any());
  }

  private static User persistedUser(String cognitoId, String email) {
    User user = User.createFromCognitoIdentity(cognitoId, "Nome", email);
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    return user;
  }

  private static Conversation persistedConversation(User owner) {
    Conversation conversation = Conversation.startForUser(owner, "Conversa");
    ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
    return conversation;
  }

  private static AuthenticatedIdentity identity(User user) {
    return new AuthenticatedIdentity(user.getCognitoId(), user.getName(), user.getEmail());
  }
}
