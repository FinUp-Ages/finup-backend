package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.DismissSpendingAlertResponse;
import br.com.finup.dto.SpendingAlertItemResponse;
import br.com.finup.dto.SpendingAlertProjectionResponse;
import br.com.finup.dto.SpendingAlertsResponse;
import br.com.finup.exception.InvalidSpendingAlertPeriodException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.AlertSource;
import br.com.finup.model.RiskKind;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.SpendingAlertService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(SpendingAlertController.class)
class SpendingAlertControllerTest {

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("mock-sub", "Gabriel", "gabriel@exemplo.com");

  private static final Instant DISMISSED_AT = Instant.parse("2026-10-21T12:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SpendingAlertService spendingAlertService;
  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @BeforeEach
  void setUp() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("GET /api/v1/insights/spending-alerts?days=30 devolve formato exato do contrato C1")
  void getSpendingAlertsReturnsContractC1Format() throws Exception {
    UUID categoryId = UUID.fromString("d3b07384-d113-4e4e-b5f7-9b2f6b8c9d10");
    SpendingAlertItemResponse alert =
        new SpendingAlertItemResponse(
            categoryId,
            "Apostas",
            RiskKind.BETS,
            new BigDecimal("3000.00"),
            4,
            "R$ 3.000,00 em apostas nos últimos 30 dias.",
            new SpendingAlertProjectionResponse(5, 10, new BigDecimal("4831.53")),
            AlertSource.RULE,
            DISMISSED_AT);

    SpendingAlertsResponse response = new SpendingAlertsResponse(30, List.of(alert));

    when(spendingAlertService.getSpendingAlerts(IDENTITY, 30)).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/insights/spending-alerts").param("days", "30"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.periodDays").value(30))
        .andExpect(jsonPath("$.alerts.length()").value(1))
        .andExpect(jsonPath("$.alerts[0].categoryId").value(categoryId.toString()))
        .andExpect(jsonPath("$.alerts[0].categoryName").value("Apostas"))
        .andExpect(jsonPath("$.alerts[0].riskKind").value("BETS"))
        .andExpect(jsonPath("$.alerts[0].totalAmount").value(3000.00))
        .andExpect(jsonPath("$.alerts[0].transactionCount").value(4))
        .andExpect(
            jsonPath("$.alerts[0].message").value("R$ 3.000,00 em apostas nos últimos 30 dias."))
        .andExpect(jsonPath("$.alerts[0].projection.years").value(5))
        .andExpect(jsonPath("$.alerts[0].projection.annualRatePct").value(10))
        .andExpect(jsonPath("$.alerts[0].projection.futureValue").value(4831.53))
        .andExpect(jsonPath("$.alerts[0].source").value("RULE"))
        .andExpect(jsonPath("$.alerts[0].dismissedAt").value("2026-10-21T12:00:00Z"));

    verify(spendingAlertService).getSpendingAlerts(IDENTITY, 30);
  }

  @Test
  @DisplayName("GET /api/v1/insights/spending-alerts sem parametro days assume default 30")
  void getSpendingAlertsDefaultsDaysTo30() throws Exception {
    SpendingAlertsResponse response = new SpendingAlertsResponse(30, List.of());
    when(spendingAlertService.getSpendingAlerts(IDENTITY, 30)).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/insights/spending-alerts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.periodDays").value(30))
        .andExpect(jsonPath("$.alerts").isEmpty());

    verify(spendingAlertService).getSpendingAlerts(IDENTITY, 30);
  }

  @Test
  @DisplayName("GET com days <= 0 devolve 400 ProblemDetail")
  void getSpendingAlertsWithInvalidDaysReturns400() throws Exception {
    when(spendingAlertService.getSpendingAlerts(eq(IDENTITY), eq(0)))
        .thenThrow(
            new InvalidSpendingAlertPeriodException("O parametro days deve ser maior que zero."));

    mockMvc
        .perform(get("/api/v1/insights/spending-alerts").param("days", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("O parametro days deve ser maior que zero."));
  }

  @Test
  @DisplayName("GET de usuario sem cadastro local devolve 404")
  void getSpendingAlertsWithoutLocalUserReturns404() throws Exception {
    when(spendingAlertService.getSpendingAlerts(IDENTITY, 30))
        .thenThrow(new ResourceNotFoundException("Usuario nao encontrado"));

    mockMvc
        .perform(get("/api/v1/insights/spending-alerts").param("days", "30"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("POST dismiss devolve 200 com a categoria e o instante da dispensa")
  void dismissReturns200() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(spendingAlertService.dismissAlert(IDENTITY, categoryId))
        .thenReturn(new DismissSpendingAlertResponse(categoryId, DISMISSED_AT));

    mockMvc
        .perform(
            post("/api/v1/insights/spending-alerts/dismiss")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + categoryId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
        .andExpect(jsonPath("$.dismissedAt").value("2026-10-21T12:00:00Z"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"categoryId\":null}", "{\"categoryId\":\"nao-e-uuid\"}"})
  @DisplayName("POST dismiss com categoryId ausente ou invalido devolve 400 sem chegar ao service")
  void dismissWithInvalidBodyReturns400(String body) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/insights/spending-alerts/dismiss")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verify(spendingAlertService, never()).dismissAlert(any(), any());
  }

  @Test
  @DisplayName("POST dismiss de categoria inexistente ou alheia devolve 404")
  void dismissUnknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(spendingAlertService.dismissAlert(IDENTITY, categoryId))
        .thenThrow(new ResourceNotFoundException("Categoria não encontrada."));

    mockMvc
        .perform(
            post("/api/v1/insights/spending-alerts/dismiss")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + categoryId + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
