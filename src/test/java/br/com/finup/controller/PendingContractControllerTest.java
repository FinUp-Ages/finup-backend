package br.com.finup.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Rotas com contrato publicado e implementacao pendente: validam a entrada e respondem 501. */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest({AssistantController.class, TransactionController.class})
class PendingContractControllerTest {

  @Autowired private MockMvc mockMvc;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private br.com.finup.service.TransactionService transactionService;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private br.com.finup.security.AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @Test
  @DisplayName("GET /transactions responde 501")
  void listTransactionsNotImplemented() throws Exception {
    mockMvc.perform(get("/api/v1/transactions")).andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName("GET /transactions com size acima de 100 responde 400")
  void listTransactionsRejectsOversizedPage() throws Exception {
    mockMvc
        .perform(get("/api/v1/transactions").param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /assistant/messages valido responde 501")
  void assistantNotImplemented() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assistant/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Quanto gastei?\"}"))
        .andExpect(status().isNotImplemented());
  }

  @Test
  @DisplayName("POST /assistant/messages com mensagem em branco responde 400")
  void assistantRejectsBlankMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/assistant/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\" \"}"))
        .andExpect(status().isBadRequest());
  }
}
