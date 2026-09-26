package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.InvalidRecurrencePeriodException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.TransactionRecurrence;
import br.com.finup.model.TransactionType;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.TransactionRecurrenceService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** {@code @WebMvcTest} sobe so a camada web: sem banco, service mockado. */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TransactionRecurrenceController.class)
class TransactionRecurrenceControllerTest {

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TransactionRecurrenceService transactionRecurrenceService;

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @BeforeEach
  void mockIdentity() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("POST valido devolve 201 com Location e o corpo da recorrencia")
  void validRegistrationReturns201() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    TransactionRecurrence recurrence =
        TransactionRecurrence.register(
            userId,
            categoryId,
            null,
            TransactionType.EXPENSE,
            "Conta de luz",
            new BigDecimal("250.00"),
            RecurrenceFrequency.MONTHLY,
            5,
            LocalDate.of(2026, 1, 1),
            null);
    when(transactionRecurrenceService.register(
            eq(IDENTITY),
            eq(categoryId),
            eq(null),
            eq(TransactionType.EXPENSE),
            eq("Conta de luz"),
            eq(new BigDecimal("250.00")),
            eq(RecurrenceFrequency.MONTHLY),
            eq(5),
            eq(LocalDate.of(2026, 1, 1)),
            eq(null)))
        .thenReturn(recurrence);

    mockMvc
        .perform(
            post("/api/v1/transaction-recurrences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "EXPENSE",
                      "description": "Conta de luz",
                      "amount": 250.00,
                      "frequency": "MONTHLY",
                      "dayOfMonth": 5,
                      "startDate": "2026-01-01"
                    }
                    """
                        .formatted(categoryId)))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", "/api/v1/transaction-recurrences/" + recurrence.getId()))
        .andExpect(jsonPath("$.id").value(recurrence.getId().toString()))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.dayOfMonth").value(5));
  }

  @Test
  @DisplayName("POST com campos invalidos devolve 400 em RFC 7807, sem chamar o service")
  void invalidRegistrationReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transaction-recurrences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": null,
                      "amount": -10,
                      "dayOfMonth": 40
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Requisicao invalida"));
  }

  @Test
  @DisplayName("categoria inexistente vira 404, e nao 500")
  void unknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(transactionRecurrenceService.register(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any()))
        .thenThrow(new ResourceNotFoundException("Categoria", categoryId));

    mockMvc
        .perform(
            post("/api/v1/transaction-recurrences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "EXPENSE",
                      "amount": 250.00,
                      "frequency": "MONTHLY",
                      "dayOfMonth": 5,
                      "startDate": "2026-01-01"
                    }
                    """
                        .formatted(categoryId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("periodo invalido vira 422")
  void invalidPeriodReturns422() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(transactionRecurrenceService.register(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any()))
        .thenThrow(new InvalidRecurrencePeriodException());

    mockMvc
        .perform(
            post("/api/v1/transaction-recurrences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "EXPENSE",
                      "amount": 250.00,
                      "frequency": "MONTHLY",
                      "dayOfMonth": 5,
                      "startDate": "2026-09-01",
                      "endDate": "2026-01-01"
                    }
                    """
                        .formatted(categoryId)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("GET /due lista as recorrencias do usuario autenticado que caem na data informada")
  void findDueReturnsMatchingRecurrences() throws Exception {
    UUID userId = UUID.randomUUID();
    TransactionRecurrence recurrence =
        TransactionRecurrence.register(
            userId,
            UUID.randomUUID(),
            null,
            TransactionType.EXPENSE,
            "Conta de luz",
            new BigDecimal("250.00"),
            RecurrenceFrequency.MONTHLY,
            5,
            LocalDate.of(2026, 1, 1),
            null);
    when(transactionRecurrenceService.findDueOn(IDENTITY, LocalDate.of(2026, 9, 5)))
        .thenReturn(List.of(recurrence));

    mockMvc
        .perform(get("/api/v1/transaction-recurrences/due").param("date", "2026-09-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(recurrence.getId().toString()));
  }
}
