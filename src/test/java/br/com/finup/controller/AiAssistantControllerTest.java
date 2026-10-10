package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.AiAssistantRequest;
import br.com.finup.dto.AiAssistantResponse;
import br.com.finup.exception.AiProviderException;
import br.com.finup.exception.BusinessException;
import br.com.finup.model.AiModel;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.AiAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica o contrato HTTP do assistente de IA. */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AiAssistantController.class)
class AiAssistantControllerTest {

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

  private static final java.util.UUID CONVERSATION_ID =
      java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AiAssistantService aiAssistantService;
  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @BeforeEach
  void mockIdentity() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("POST valido devolve 200 com a acao executada")
  void validRequestReturns200() throws Exception {
    when(aiAssistantService.handle(
            eq(IDENTITY),
            eq(new AiAssistantRequest("gastei 7 reais na pucrs", AiModel.ANTHROPIC, null))))
        .thenReturn(
            new AiAssistantResponse(
                "REGISTER_TRANSACTION",
                "Despesa de R$ 7.00 registrada em Educação.",
                null,
                CONVERSATION_ID));

    mockMvc
        .perform(
            post("/api/v1/ai/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"gastei 7 reais na pucrs\",\"model\":\"ANTHROPIC\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("REGISTER_TRANSACTION"))
        .andExpect(jsonPath("$.message").value("Despesa de R$ 7.00 registrada em Educação."))
        .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()));
  }

  @Test
  @DisplayName("conversationId vazio (variavel do Postman ainda sem valor) vale como conversa nova")
  void blankConversationIdIsTreatedAsNewConversation() throws Exception {
    when(aiAssistantService.handle(eq(IDENTITY), eq(new AiAssistantRequest("oi", null, null))))
        .thenReturn(new AiAssistantResponse("FINANCIAL_FEEDBACK", "ola", null, CONVERSATION_ID));

    mockMvc
        .perform(
            post("/api/v1/ai/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"oi\",\"conversationId\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()));
  }

  @Test
  @DisplayName("mensagem em branco devolve 400 sem chamar o assistente")
  void blankMessageReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ai/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("message"));
    verifyNoInteractions(aiAssistantService);
  }

  @Test
  @DisplayName("modelo inexistente devolve 400")
  void unknownModelReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ai/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"oi\",\"model\":\"FOO\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(aiAssistantService);
  }

  @Test
  @DisplayName("texto nao entendido devolve 422 em problem+json")
  void notUnderstoodReturns422() throws Exception {
    when(aiAssistantService.handle(any(), any()))
        .thenThrow(new BusinessException("Nao entendi o que voce quer fazer."));

    mockMvc
        .perform(
            post("/api/v1/ai/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"blah\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("Nao entendi o que voce quer fazer."));
  }

  @Test
  @DisplayName("falha do Bedrock devolve o status traduzido")
  void providerFailureReturnsTranslatedStatus() throws Exception {
    when(aiAssistantService.handle(any(), any()))
        .thenThrow(new AiProviderException("Limite excedido.", HttpStatus.TOO_MANY_REQUESTS, null));

    mockMvc
        .perform(
            post("/api/v1/ai/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"gastei 7\"}"))
        .andExpect(status().isTooManyRequests());
  }
}
