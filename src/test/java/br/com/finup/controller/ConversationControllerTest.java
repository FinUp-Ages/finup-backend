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

import br.com.finup.dto.ConversationResponse;
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
        new ConversationResponse(newerId, "Mais recente", Instant.parse("2026-09-26T10:00:00Z"), 4);
    ConversationResponse older =
        new ConversationResponse(olderId, "Mais antiga", Instant.parse("2026-09-20T10:00:00Z"), 2);
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
  @DisplayName("Lista as mensagens de uma conversa")
  void shouldReturnConversationMessages() throws Exception {
    UUID id = UUID.randomUUID();
    when(conversationService.findMessages(any(AuthenticatedIdentity.class), eq(id)))
        .thenReturn(
            List.of(
                new br.com.finup.dto.ConversationMessageResponse(
                    br.com.finup.model.ChatMessage.Role.USER,
                    "gastei 7 reais na pucrs",
                    null,
                    Instant.parse("2026-10-02T10:00:00Z")),
                new br.com.finup.dto.ConversationMessageResponse(
                    br.com.finup.model.ChatMessage.Role.ASSISTANT,
                    "Despesa registrada.",
                    "REGISTER_TRANSACTION",
                    Instant.parse("2026-10-02T10:00:01Z"))));

    mockMvc
        .perform(get(URL + "/" + id + "/messages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].role").value("USER"))
        .andExpect(jsonPath("$[1].action").value("REGISTER_TRANSACTION"));
  }

  @Test
  @DisplayName("Conversa inexistente ou de outro usuario devolve 404")
  void shouldReturn404ForForeignConversation() throws Exception {
    UUID id = UUID.randomUUID();
    when(conversationService.findMessages(any(AuthenticatedIdentity.class), eq(id)))
        .thenThrow(new br.com.finup.exception.ResourceNotFoundException("Conversa", id));

    mockMvc.perform(get(URL + "/" + id + "/messages")).andExpect(status().isNotFound());
  }
}
