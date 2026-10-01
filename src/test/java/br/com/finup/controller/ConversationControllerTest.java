package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.dto.ConversationResponse;
import br.com.finup.exception.ForbiddenOperationException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.MessageRole;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.ConversationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean ConversationService conversationService;

  @MockitoBean AuthenticatedIdentityResolver authenticatedIdentityResolver;

  private static final String URL = "/api/v1/assistant/conversations";

  @BeforeEach
  void setUp() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("mock-sub-usuario-teste", "Usuário Teste", "teste@finup.local");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
  }

  @Test
  @DisplayName("GET retorna HTTP 200")
  void shouldReturn200() throws Exception {
    when(conversationService.findHistory(any(AuthenticatedIdentity.class))).thenReturn(List.of());

    mockMvc.perform(get(URL)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Retorna lista vazia quando o usuario nao tem conversas")
  void shouldReturnEmptyList() throws Exception {
    when(conversationService.findHistory(any(AuthenticatedIdentity.class))).thenReturn(List.of());

    mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().json("[]"));
  }

  @Test
  @DisplayName("Lista as conversas do usuario, na ordem devolvida pelo service")
  void shouldReturnConversationsInServiceOrder() throws Exception {
    UUID newerId = UUID.randomUUID();
    UUID olderId = UUID.randomUUID();
    ConversationResponse newer =
        new ConversationResponse(newerId, "Mais recente", Instant.parse("2026-09-26T10:00:00Z"));
    ConversationResponse older =
        new ConversationResponse(olderId, "Mais antiga", Instant.parse("2026-09-20T10:00:00Z"));
    when(conversationService.findHistory(any(AuthenticatedIdentity.class)))
        .thenReturn(List.of(newer, older));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(newerId.toString()))
        .andExpect(jsonPath("$[0].title").value("Mais recente"))
        .andExpect(jsonPath("$[1].id").value(olderId.toString()));
  }

  @Test
  @DisplayName("Identidade e resolvida via AuthenticatedIdentityResolver")
  void shouldResolveIdentityViaResolver() throws Exception {
    when(conversationService.findHistory(any(AuthenticatedIdentity.class))).thenReturn(List.of());

    mockMvc.perform(get(URL)).andExpect(status().isOk());

    verify(authenticatedIdentityResolver, times(1)).resolveCurrent();
  }

  @Test
  @DisplayName("GET de mensagens retorna HTTP 200 com as mensagens na ordem devolvida pelo service")
  void shouldReturnMessagesInServiceOrder() throws Exception {
    UUID conversationId = UUID.randomUUID();
    UUID questionId = UUID.randomUUID();
    UUID answerId = UUID.randomUUID();
    ConversationMessageResponse question =
        new ConversationMessageResponse(
            questionId, MessageRole.USER, "Quanto gastei?", Instant.parse("2026-09-26T10:00:00Z"));
    ConversationMessageResponse answer =
        new ConversationMessageResponse(
            answerId, MessageRole.ASSISTANT, "R$ 820,00", Instant.parse("2026-09-26T10:00:05Z"));
    when(conversationService.findMessages(any(AuthenticatedIdentity.class), eq(conversationId)))
        .thenReturn(List.of(question, answer));

    mockMvc
        .perform(get(URL + "/{id}/messages", conversationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(questionId.toString()))
        .andExpect(jsonPath("$[0].role").value("USER"))
        .andExpect(jsonPath("$[0].content").value("Quanto gastei?"))
        .andExpect(jsonPath("$[0].createdAt").value("2026-09-26T10:00:00Z"))
        .andExpect(jsonPath("$[1].id").value(answerId.toString()))
        .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
  }

  @Test
  @DisplayName("GET de mensagens de conversa sem mensagens retorna lista vazia")
  void shouldReturnEmptyMessageList() throws Exception {
    UUID conversationId = UUID.randomUUID();
    when(conversationService.findMessages(any(AuthenticatedIdentity.class), eq(conversationId)))
        .thenReturn(List.of());

    mockMvc
        .perform(get(URL + "/{id}/messages", conversationId))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  @DisplayName("GET de mensagens de conversa de outro usuario retorna 403 em RFC 7807")
  void shouldReturn403ForAnotherUsersConversation() throws Exception {
    UUID conversationId = UUID.randomUUID();
    when(conversationService.findMessages(any(AuthenticatedIdentity.class), eq(conversationId)))
        .thenThrow(new ForbiddenOperationException("A conversa pertence a outro usuário"));

    mockMvc
        .perform(get(URL + "/{id}/messages", conversationId))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName("GET de mensagens de conversa inexistente retorna 404 em RFC 7807")
  void shouldReturn404ForMissingConversation() throws Exception {
    UUID conversationId = UUID.randomUUID();
    when(conversationService.findMessages(any(AuthenticatedIdentity.class), eq(conversationId)))
        .thenThrow(new ResourceNotFoundException("Conversa", conversationId));

    mockMvc
        .perform(get(URL + "/{id}/messages", conversationId))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("GET de mensagens com id que nao e UUID retorna 400 sem chamar o service")
  void shouldReturn400ForInvalidConversationId() throws Exception {
    mockMvc.perform(get(URL + "/{id}/messages", "nao-e-uuid")).andExpect(status().isBadRequest());

    verify(conversationService, times(0)).findMessages(any(), any());
  }
}
