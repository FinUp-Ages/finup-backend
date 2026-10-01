package br.com.finup.controller;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.TransactionListItemResponse;
import br.com.finup.dto.TransactionListResponse;
import br.com.finup.exception.IncompatibleTransactionCategoryException;
import br.com.finup.exception.InvalidTransactionRecurrenceException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.TransactionService;
import br.com.finup.service.UserService;
import java.math.BigDecimal;
import java.time.Instant;
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

/** Verifica o contrato HTTP do cadastro de transacoes e seus erros esperados. */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TransactionService transactionService;

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @MockitoBean private UserService userService;

  @BeforeEach
  void mockIdentity() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("GET das proprias transacoes devolve 200 com saldo e historico")
  void ownTransactionsReturn200() throws Exception {
    User user = User.createFromCognitoIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

    UUID userId = user.getId();
    UUID transactionId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();

    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);
    LocalDate transactionDate = LocalDate.of(2026, 9, 5);

    TransactionListItemResponse transactionResponse =
        new TransactionListItemResponse(
            transactionId,
            categoryId,
            null,
            TransactionType.INCOME,
            "Salario",
            new BigDecimal("3000.00"),
            transactionDate,
            false,
            Instant.parse("2026-09-05T10:00:00Z"));

    TransactionListResponse response =
        new TransactionListResponse(new BigDecimal("3000.00"), List.of(transactionResponse));

    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(user);

    when(transactionService.list(userId, from, to)).thenReturn(response);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .param("userId", userId.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(3000.00))
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].id").value(transactionId.toString()))
        .andExpect(jsonPath("$.transactions[0].categoryId").value(categoryId.toString()))
        .andExpect(jsonPath("$.transactions[0].type").value("INCOME"))
        .andExpect(jsonPath("$.transactions[0].description").value("Salario"))
        .andExpect(jsonPath("$.transactions[0].amount").value(3000.00))
        .andExpect(jsonPath("$.transactions[0].transactionDate").value("2026-09-05"))
        .andExpect(jsonPath("$.transactions[0].isRecurring").value(false))
        .andExpect(jsonPath("$.transactions[0].paymentMethodId").doesNotExist())
        .andExpect(jsonPath("$.transactions[0].createdAt").value("2026-09-05T10:00:00Z"));
  }

  @Test
  @DisplayName("GET de outro usuario sem permissao devolve 403")
  void otherUserTransactionsReturn403() throws Exception {
    User authenticatedUser =
        User.createFromCognitoIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

    UUID otherUserId = UUID.randomUUID();
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);

    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(authenticatedUser);

    when(authenticatedIdentityResolver.isCurrentUserAdmin()).thenReturn(false);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .param("userId", otherUserId.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(
            jsonPath("$.detail").value("Nao e permitido consultar transacoes de outro usuario."));
  }

  @Test
  @DisplayName("GET de outro usuario por admin devolve 200")
  void adminCanAccessOtherUserTransactions() throws Exception {
    User authenticatedUser =
        User.createFromCognitoIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

    UUID otherUserId = UUID.randomUUID();
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);

    TransactionListResponse response = new TransactionListResponse(BigDecimal.ZERO, List.of());

    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(authenticatedUser);

    when(authenticatedIdentityResolver.isCurrentUserAdmin()).thenReturn(true);

    when(transactionService.list(otherUserId, from, to)).thenReturn(response);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .param("userId", otherUserId.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(0))
        .andExpect(jsonPath("$.transactions.length()").value(0));
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
            RecurrenceFrequency.MONTHLY);
    when(transactionService.register(
            eq(IDENTITY),
            eq(categoryId),
            isNull(),
            eq(TransactionType.INCOME),
            eq("Salario"),
            eq(new BigDecimal("5000.00")),
            eq(date),
            eq(true),
            eq(RecurrenceFrequency.MONTHLY)))
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
                      "isRecurring": true,
                      "recurrenceFrequency": "MONTHLY"
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
        .andExpect(jsonPath("$.isRecurring").value(true))
        .andExpect(jsonPath("$.recurrenceFrequency").value("MONTHLY"))
        .andExpect(jsonPath("$.lastOccurrenceDate").value("2026-09-12"))
        .andExpect(jsonPath("$.nextOccurrenceDate").value("2026-10-12"))
        .andExpect(jsonPath("$.recurrenceOriginId").doesNotExist());
  }

  @Test
  @DisplayName("isRecurring contradizendo a periodicidade devolve 422 no formato RFC 7807")
  void contradictoryRecurrenceReturns422() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(transactionService.register(
            eq(IDENTITY),
            eq(categoryId),
            isNull(),
            eq(TransactionType.EXPENSE),
            isNull(),
            eq(new BigDecimal("10.00")),
            eq(LocalDate.of(2026, 9, 12)),
            eq(true),
            isNull()))
        .thenThrow(
            new InvalidTransactionRecurrenceException(
                "Transacao recorrente precisa informar recurrenceFrequency"));

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
                      "isRecurring": true
                    }
                    """
                        .formatted(categoryId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(
            jsonPath("$.detail")
                .value("Transacao recorrente precisa informar recurrenceFrequency"));
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
  @DisplayName("categoria com tipo incompativel devolve 422 no formato RFC 7807, nunca 201")
  void incompatibleCategoryTypeReturns422() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(transactionService.register(
            eq(IDENTITY),
            eq(categoryId),
            isNull(),
            eq(TransactionType.EXPENSE),
            isNull(),
            eq(new BigDecimal("10.00")),
            eq(LocalDate.of(2026, 9, 12)),
            eq(false),
            isNull()))
        .thenThrow(
            new IncompatibleTransactionCategoryException(
                TransactionType.INCOME, TransactionType.EXPENSE));

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
        .andExpect(status().isUnprocessableEntity())
        .andExpect(status().is(not(201)))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(
            jsonPath("$.detail")
                .value("Categoria do tipo INCOME nao pode ser usada em transacao do tipo EXPENSE"))
        .andExpect(jsonPath("$.traceId").exists());
  }

  /**
   * Regressao do contrato de identidade: {@code userId} nao faz parte do corpo, entao um {@code
   * userId} enviado pelo cliente e ignorado e a transacao sai no nome de quem esta autenticado.
   */
  @Test
  @DisplayName("userId enviado no corpo e ignorado: a transacao fica com o usuario autenticado")
  void userIdInBodyIsIgnored() throws Exception {
    UUID authenticatedUserId = UUID.randomUUID();
    UUID spoofedUserId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 9, 12);
    Transaction transaction =
        Transaction.register(
            authenticatedUserId,
            categoryId,
            null,
            TransactionType.EXPENSE,
            "Mercado",
            new BigDecimal("250.00"),
            date,
            null);
    when(transactionService.register(
            eq(IDENTITY),
            eq(categoryId),
            isNull(),
            eq(TransactionType.EXPENSE),
            eq("Mercado"),
            eq(new BigDecimal("250.00")),
            eq(date),
            eq(false),
            isNull()))
        .thenReturn(transaction);

    mockMvc
        .perform(
            post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userId": "%s",
                      "categoryId": "%s",
                      "type": "EXPENSE",
                      "description": "Mercado",
                      "amount": 250.00,
                      "transactionDate": "2026-09-12",
                      "isRecurring": false
                    }
                    """
                        .formatted(spoofedUserId, categoryId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(authenticatedUserId.toString()))
        .andExpect(jsonPath("$.userId").value(not(spoofedUserId.toString())));
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
            eq(false),
            isNull()))
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
