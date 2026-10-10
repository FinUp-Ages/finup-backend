package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.dto.ConversationResponse;
import br.com.finup.exception.ConversationStorageException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.ChatMessage;
import br.com.finup.model.Conversation;
import br.com.finup.model.User;
import br.com.finup.repository.ConversationRepository;
import br.com.finup.repository.InMemoryConversationTranscriptStore;
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

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("cognito-sub", "Ana Souza", "ana@example.com");

  @Mock private ConversationRepository conversationRepository;

  @Mock private UserService userService;

  private final InMemoryConversationTranscriptStore transcriptStore =
      new InMemoryConversationTranscriptStore();

  private final User user =
      User.createFromCognitoIdentity("cognito-sub", "Ana Souza", "ana@example.com");

  private ConversationService conversationService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    conversationService =
        new ConversationService(conversationRepository, userService, transcriptStore);
  }

  private Conversation conversationWithId(String title) {
    Conversation conversation = Conversation.startForUser(user, title);
    ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
    return conversation;
  }

  @Test
  @DisplayName("findHistory devolve as conversas do usuario autenticado na ordem do repositorio")
  void findHistoryReturnsUserConversationsInRepositoryOrder() {
    Conversation newer = Conversation.startForUser(user, "Mais recente");
    Conversation older = Conversation.startForUser(user, "Mais antiga");
    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(user);
    when(conversationRepository.findByUserOrderByUpdatedAtDesc(user))
        .thenReturn(List.of(newer, older));

    List<ConversationResponse> history = conversationService.findHistory(IDENTITY);

    assertThat(history)
        .extracting(ConversationResponse::title)
        .containsExactly("Mais recente", "Mais antiga");
  }

  @Test
  @DisplayName(
      "recordTurn sem conversa cria uma, com titulo da primeira mensagem, e grava o transcript")
  void recordTurnStartsNewConversation() {
    when(conversationRepository.save(any(Conversation.class)))
        .thenAnswer(
            invocation -> {
              Conversation saved = invocation.getArgument(0);
              if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              }
              return saved;
            });

    UUID id =
        conversationService.recordTurn(
            user, null, "gastei 7 reais na pucrs", "REGISTER_TRANSACTION", "Despesa registrada.");

    List<ChatMessage> transcript = transcriptStore.read(user.getId(), id);
    assertThat(transcript)
        .extracting(ChatMessage::role, ChatMessage::text, ChatMessage::action)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                ChatMessage.Role.USER, "gastei 7 reais na pucrs", null),
            org.assertj.core.groups.Tuple.tuple(
                ChatMessage.Role.ASSISTANT, "Despesa registrada.", "REGISTER_TRANSACTION"));
    verify(conversationRepository, org.mockito.Mockito.atLeastOnce()).save(any(Conversation.class));
  }

  @Test
  @DisplayName("recordTurn numa conversa existente acrescenta ao transcript e conta as mensagens")
  void recordTurnAppendsToExistingConversation() {
    Conversation conversation = conversationWithId("Primeira");
    when(conversationRepository.findByIdAndUserForUpdate(conversation.getId(), user))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.save(conversation)).thenReturn(conversation);

    conversationService.recordTurn(
        user, conversation.getId(), "primeira", "FINANCIAL_FEEDBACK", "r1");
    conversationService.recordTurn(
        user, conversation.getId(), "segunda", "FINANCIAL_FEEDBACK", "r2");

    assertThat(conversation.getMessageCount()).isEqualTo(4);
    assertThat(transcriptStore.read(user.getId(), conversation.getId()))
        .extracting(ChatMessage::text)
        .containsExactly("primeira", "r1", "segunda", "r2");
  }

  @Test
  @DisplayName("conversa de outro usuario responde 404 e nada e gravado")
  void foreignConversationIs404() {
    UUID foreign = UUID.randomUUID();
    when(conversationRepository.findByIdAndUserForUpdate(foreign, user))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.recordTurn(user, foreign, "oi", "X", "ola"))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(transcriptStore.read(user.getId(), foreign)).isEmpty();
    verify(conversationRepository, never()).save(any(Conversation.class));
  }

  @Test
  @DisplayName("findMessages devolve as mensagens do transcript da conversa do usuario")
  void findMessagesReturnsTranscript() {
    Conversation conversation = conversationWithId("Conta");
    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(user);
    when(conversationRepository.findByIdAndUser(conversation.getId(), user))
        .thenReturn(Optional.of(conversation));
    transcriptStore.write(
        user.getId(),
        conversation.getId(),
        List.of(
            new ChatMessage(
                ChatMessage.Role.USER,
                "oi",
                null,
                java.time.Instant.parse("2026-10-02T10:00:00Z"))));

    List<ConversationMessageResponse> messages =
        conversationService.findMessages(IDENTITY, conversation.getId());

    assertThat(messages).extracting(ConversationMessageResponse::text).containsExactly("oi");
  }

  @Test
  @DisplayName("findMessages de conversa alheia responde 404 sem tocar no transcript")
  void findMessagesOfForeignConversationIs404() {
    UUID foreign = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(user);
    when(conversationRepository.findByIdAndUser(foreign, user)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.findMessages(IDENTITY, foreign))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("falha no armazenamento do transcript propaga e a conversa nao e contada")
  void storageFailurePropagatesWithoutCounting() {
    Conversation conversation = conversationWithId("Conta");
    when(conversationRepository.findByIdAndUserForUpdate(conversation.getId(), user))
        .thenReturn(Optional.of(conversation));
    br.com.finup.repository.ConversationTranscriptStore failing =
        new br.com.finup.repository.ConversationTranscriptStore() {
          @Override
          public List<ChatMessage> read(UUID userId, UUID conversationId) {
            return List.of();
          }

          @Override
          public void write(UUID userId, UUID conversationId, List<ChatMessage> messages) {
            throw new ConversationStorageException(new RuntimeException("s3 fora do ar"));
          }
        };
    ConversationService service =
        new ConversationService(conversationRepository, userService, failing);

    assertThatThrownBy(() -> service.recordTurn(user, conversation.getId(), "oi", "X", "ola"))
        .isInstanceOf(ConversationStorageException.class);
    assertThat(conversation.getMessageCount()).isZero();
  }

  @Test
  @DisplayName("titulo e o recorte da primeira mensagem, sem espacos extras e com limite")
  void titleIsTrimmedFirstMessage() {
    assertThat(ConversationService.titleFrom("  gastei   7 reais \n na pucrs "))
        .isEqualTo("gastei 7 reais na pucrs");
    String title = ConversationService.titleFrom("a".repeat(200));
    assertThat(title).hasSize(60).endsWith("…");
  }
}
