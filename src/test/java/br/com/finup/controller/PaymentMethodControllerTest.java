package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.finup.dto.PaymentMethodResponse;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.PaymentMethodService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentMethodController.class)
class PaymentMethodControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean PaymentMethodService paymentMethodService;

  @MockBean AuthenticatedIdentityResolver authenticatedIdentityResolver;

  private static final String URL = "/api/v1/payment-methods";

  @BeforeEach
  void setUp() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("mock-sub-usuario-teste", "Usuário Teste", "teste@finup.local");
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(identity);
  }

  @Test
  @DisplayName("GET retorna HTTP 200")
  void shouldReturn200() throws Exception {
    when(paymentMethodService.findActiveByUser(any(AuthenticatedIdentity.class)))
        .thenReturn(List.of());

    mockMvc.perform(get(URL)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Retorna lista vazia quando usuário não tem métodos")
  void shouldReturnEmptyList() throws Exception {
    when(paymentMethodService.findActiveByUser(any(AuthenticatedIdentity.class)))
        .thenReturn(List.of());

    mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().json("[]"));
  }

  @Test
  @DisplayName("Retorna somente id e name na resposta")
  void shouldReturnOnlyIdAndName() throws Exception {
    UUID id = UUID.randomUUID();
    when(paymentMethodService.findActiveByUser(any(AuthenticatedIdentity.class)))
        .thenReturn(List.of(new PaymentMethodResponse(id, "Apple Pay")));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id.toString()))
        .andExpect(jsonPath("$[0].name").value("Apple Pay"))
        .andExpect(jsonPath("$[0].userId").doesNotExist())
        .andExpect(jsonPath("$[0].type").doesNotExist())
        .andExpect(jsonPath("$[0].institution").doesNotExist())
        .andExpect(jsonPath("$[0].creditLimit").doesNotExist());
  }

  @Test
  @DisplayName("Retorna os três métodos seedados do usuário")
  void shouldReturnSeededMethods() throws Exception {
    List<PaymentMethodResponse> methods =
        List.of(
            new PaymentMethodResponse(UUID.randomUUID(), "Cartão Teste"),
            new PaymentMethodResponse(UUID.randomUUID(), "Apple Pay"),
            new PaymentMethodResponse(UUID.randomUUID(), "Cartão de Débito"));
    when(paymentMethodService.findActiveByUser(any(AuthenticatedIdentity.class)))
        .thenReturn(methods);

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Cartão Teste"))
        .andExpect(jsonPath("$[1].name").value("Apple Pay"))
        .andExpect(jsonPath("$[2].name").value("Cartão de Débito"));
  }

  @Test
  @DisplayName("Identidade é resolvida via AuthenticatedIdentityResolver")
  void shouldResolveIdentityViaResolver() throws Exception {
    when(paymentMethodService.findActiveByUser(any(AuthenticatedIdentity.class)))
        .thenReturn(List.of());

    mockMvc.perform(get(URL)).andExpect(status().isOk());

    verify(authenticatedIdentityResolver, times(1)).resolveCurrent();
  }
}
