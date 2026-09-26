package br.com.finup.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.FinUpScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mesmo estilo do {@code UserControllerTest}: fatia web, service mockado. {@link
 * AuthenticatedIdentityResolver} tambem e mockado — o controller depende da interface, nao da
 * implementacao que le os headers mock.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(FinUpScoreController.class)
class FinUpScoreControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FinUpScoreService finUpScoreService;

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity("mock-sub-ana", "Ana Souza", "ana@exemplo.com");

  @Test
  @DisplayName("score computado devolve 200 com status COMPUTED e o valor calculado")
  void computedScoreReturns200() throws Exception {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(finUpScoreService.recalculate(identity)).thenReturn(new FinUpScoreResult.Computed(696));

    mockMvc
        .perform(post("/api/v1/users/me/finup-score/recalculate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPUTED"))
        .andExpect(jsonPath("$.score").value(696));
  }

  @Test
  @DisplayName("dado insuficiente devolve 200 com status INSUFFICIENT_DATA e score nulo")
  void insufficientDataReturns200() throws Exception {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(finUpScoreService.recalculate(identity))
        .thenReturn(new FinUpScoreResult.InsufficientData("renda ausente"));

    mockMvc
        .perform(post("/api/v1/users/me/finup-score/recalculate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"))
        .andExpect(jsonPath("$.score").doesNotExist())
        .andExpect(jsonPath("$.message").value("renda ausente"));
  }

  @Test
  @DisplayName("usuario ainda nao criado devolve 404 em RFC 7807")
  void unknownUserReturns404() throws Exception {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
    when(finUpScoreService.recalculate(identity))
        .thenThrow(
            new ResourceNotFoundException("Usuario nao encontrado para a identidade autenticada."));

    mockMvc
        .perform(post("/api/v1/users/me/finup-score/recalculate"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
