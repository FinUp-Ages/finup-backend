package br.com.finup.service;

import br.com.finup.dto.ConversationResponse;
import br.com.finup.mapper.ConversationMapper;
import br.com.finup.model.User;
import br.com.finup.repository.ConversationRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final UserService userService;

  public ConversationService(
      ConversationRepository conversationRepository, UserService userService) {
    this.conversationRepository = conversationRepository;
    this.userService = userService;
  }

  /** Histórico de conversas do usuário autenticado, da mais recente para a mais antiga. */
  public List<ConversationResponse> findHistory(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    return conversationRepository.findByUserOrderByUpdatedAtDesc(user).stream()
        .map(ConversationMapper::toResponse)
        .toList();
  }
}
