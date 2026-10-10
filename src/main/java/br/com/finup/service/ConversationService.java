package br.com.finup.service;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.dto.ConversationResponse;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.ConversationMapper;
import br.com.finup.model.ChatMessage;
import br.com.finup.model.Conversation;
import br.com.finup.model.User;
import br.com.finup.repository.ConversationRepository;
import br.com.finup.repository.ConversationTranscriptStore;
import br.com.finup.security.AuthenticatedIdentity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historico de conversas do assistente: metadado no banco, corpo das mensagens no S3 (via {@link
 * ConversationTranscriptStore}).
 *
 * <p>Uma conversa de outro usuario responde 404, nao 403: um 403 confirmaria que o id existe.
 */
@Service
public class ConversationService {

  private static final int MAX_TITLE = 60;

  private final ConversationRepository conversationRepository;
  private final UserService userService;
  private final ConversationTranscriptStore transcriptStore;

  public ConversationService(
      ConversationRepository conversationRepository,
      UserService userService,
      ConversationTranscriptStore transcriptStore) {
    this.conversationRepository = conversationRepository;
    this.userService = userService;
    this.transcriptStore = transcriptStore;
  }

  /** Histórico de conversas do usuário autenticado, da mais recente para a mais antiga. */
  public List<ConversationResponse> findHistory(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    return conversationRepository.findByUserOrderByUpdatedAtDesc(user).stream()
        .map(ConversationMapper::toResponse)
        .toList();
  }

  /**
   * Mensagens de uma conversa do usuário autenticado, em ordem.
   *
   * @throws ResourceNotFoundException se a conversa não existir ou for de outro usuário
   * @throws br.com.finup.exception.ConversationStorageException se o armazenamento falhar
   */
  public List<ConversationMessageResponse> findMessages(
      AuthenticatedIdentity identity, UUID conversationId) {
    User user = userService.findByAuthenticatedIdentity(identity);
    Conversation conversation = requireOwned(user, conversationId);
    return transcriptStore.read(user.getId(), conversation.getId()).stream()
        .map(ConversationMapper::toResponse)
        .toList();
  }

  /**
   * Confirma que a conversa existe e é do usuário. O assistente chama isto antes de gastar uma
   * chamada ao modelo, para uma conversa inválida falhar cedo.
   *
   * @throws ResourceNotFoundException se não existir ou for de outro usuário
   */
  public Conversation requireOwned(User user, UUID conversationId) {
    return conversationRepository
        .findByIdAndUser(conversationId, user)
        .orElseThrow(() -> new ResourceNotFoundException("Conversa", conversationId));
  }

  /**
   * Grava um turno (mensagem do usuário + resposta do assistente) na conversa; cria a conversa se
   * {@code conversationId} for nulo. Tudo numa transação: se o S3 falhar, nem a conversa nova fica
   * no banco.
   *
   * @return o id da conversa
   */
  @Transactional
  public UUID recordTurn(
      User user, UUID conversationId, String userText, String action, String assistantText) {
    Conversation conversation =
        conversationId == null
            ? conversationRepository.save(Conversation.startForUser(user, titleFrom(userText)))
            : conversationRepository
                .findByIdAndUserForUpdate(conversationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa", conversationId));

    List<ChatMessage> messages = new ArrayList<>();
    if (conversation.getMessageCount() > 0) {
      messages.addAll(transcriptStore.read(user.getId(), conversation.getId()));
    }
    Instant now = Instant.now();
    messages.add(new ChatMessage(ChatMessage.Role.USER, userText, null, now));
    messages.add(new ChatMessage(ChatMessage.Role.ASSISTANT, assistantText, action, now));

    transcriptStore.write(user.getId(), conversation.getId(), messages);
    conversation.recordMessages(2);
    conversationRepository.save(conversation);
    return conversation.getId();
  }

  /**
   * Título provisório: recorte da primeira mensagem. A estratégia definitiva (recorte vs. resumo
   * pedido ao modelo) segue pendente com o time.
   */
  static String titleFrom(String firstMessage) {
    String text = firstMessage.strip().replaceAll("\\s+", " ");
    return text.length() <= MAX_TITLE
        ? text
        : text.substring(0, MAX_TITLE - 1).stripTrailing() + "…";
  }
}
