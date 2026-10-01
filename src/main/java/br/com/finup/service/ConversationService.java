package br.com.finup.service;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.dto.ConversationResponse;
import br.com.finup.exception.ForbiddenOperationException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.ConversationMapper;
import br.com.finup.mapper.ConversationMessageMapper;
import br.com.finup.model.Conversation;
import br.com.finup.model.User;
import br.com.finup.repository.ConversationMessageRepository;
import br.com.finup.repository.ConversationRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final ConversationMessageRepository conversationMessageRepository;
  private final UserService userService;

  public ConversationService(
      ConversationRepository conversationRepository,
      ConversationMessageRepository conversationMessageRepository,
      UserService userService) {
    this.conversationRepository = conversationRepository;
    this.conversationMessageRepository = conversationMessageRepository;
    this.userService = userService;
  }

  /** Histórico de conversas do usuário autenticado, da mais recente para a mais antiga. */
  public List<ConversationResponse> findHistory(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    return conversationRepository.findByUserOrderByUpdatedAtDesc(user).stream()
        .map(ConversationMapper::toResponse)
        .toList();
  }

  /**
   * Mensagens de uma conversa do usuário autenticado, da mais antiga para a mais recente.
   *
   * <p>Conversa inexistente responde 404 e conversa de outro usuário responde 403, como define o
   * contrato desta rota. Nenhuma mensagem de outro usuário chega a ser lida do banco.
   */
  @Transactional(readOnly = true)
  public List<ConversationMessageResponse> findMessages(
      AuthenticatedIdentity identity, UUID conversationId) {
    User user = userService.findByAuthenticatedIdentity(identity);
    Conversation conversation =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Conversa nao encontrada: %s".formatted(conversationId)));

    if (!conversation.getUser().getId().equals(user.getId())) {
      throw new ForbiddenOperationException("A conversa pertence a outro usuário");
    }

    return conversationMessageRepository
        .findByConversationOrderByCreatedAtAscIdAsc(conversation)
        .stream()
        .map(ConversationMessageMapper::toResponse)
        .toList();
  }
}
