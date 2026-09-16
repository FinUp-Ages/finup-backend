package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica o contrato HTTP do cadastro de transacoes e seus erros esperados. */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TransactionService transactionService;

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @BeforeEach
  void mockIdentity() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("POST valido sem meio de pagamento devolve 201 e a transacao cadastrada")
  void validRequestWithoutPaymentMethodReturns201() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 9, 12);
    Transaction transaction =
        Transaction.register(
            userId,
            categoryId,
            null,
            TransactionType.INCOME,
            "Salario",
            new BigDecimal("5000.00"),
            date,
            true);
    when(transactionService.register(
            eq(IDENTITY),
            eq(categoryId),
            isNull(),
            eq(TransactionType.INCOME),
            eq("Salario"),
            eq(new BigDecimal("5000.00")),
            eq(date),
            eq(true)))
        .thenReturn(transaction);

    mockMvc
        .perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "INCOME",
                      "description": "Salario",
                      "amount": 5000.00,
                      "transactionDate": "2026-09-12",
                      "isRecurring": true
                    }
                    """
                        .formatted(categoryId)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/transactions/" + transaction.getId()))
        .andExpect(jsonPath("$.id").value(transaction.getId().toString()))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
        .andExpect(jsonPath("$.paymentMethodId").doesNotExist())
        .andExpect(jsonPath("$.type").value("INCOME"))
        .andExpect(jsonPath("$.amount").value(5000.00))
        .andExpect(jsonPath("$.isRecurring").value(true));
  }

  @Test
  @DisplayName("POST sem valor e data devolve 400 com os campos invalidos")
  void missingRequiredFieldsReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "EXPENSE",
                      "isRecurring": false
                    }
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Requisicao invalida"))
        .andExpect(jsonPath("$.fields.length()").value(2))
        .andExpect(jsonPath("$.fields[0].field").value("amount"))
        .andExpect(jsonPath("$.fields[1].field").value("transactionDate"));
    verifyNoInteractions(transactionService);
  }

  @Test
  @DisplayName("POST com valor negativo devolve 400, sem chegar ao service")
  void negativeAmountReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "EXPENSE",
                      "amount": -10.00,
                      "transactionDate": "2026-09-12",
                      "isRecurring": false
                    }
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("amount"))
        .andExpect(jsonPath("$.fields[0].message").value("deve ser maior que zero"));
    verifyNoInteractions(transactionService);
  }

  @Test
  @DisplayName("POST com tipo fora dos valores definidos devolve 400")
  void invalidTypeReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "TRANSFER",
                      "amount": 10.00,
                      "transactionDate": "2026-09-12",
                      "isRecurring": false
                    }
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(transactionService);
  }

  @Test
  @DisplayName("referencia inexistente devolve 404 no formato RFC 7807")
  void unknownReferenceReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 9, 12);
    when(transactionService.register(
            eq(IDENTITY),
            eq(categoryId),
            isNull(),
            eq(TransactionType.EXPENSE),
            isNull(),
            eq(new BigDecimal("10.00")),
            eq(date),
            eq(false)))
        .thenThrow(new ResourceNotFoundException("Categoria", categoryId));

    mockMvc
        .perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": "%s",
                      "type": "EXPENSE",
                      "amount": 10.00,
                      "transactionDate": "2026-09-12",
                      "isRecurring": false
                    }
                    """
                        .formatted(categoryId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Categoria nao encontrado: " + categoryId))
        .andExpect(jsonPath("$.traceId").exists());
  }
}
