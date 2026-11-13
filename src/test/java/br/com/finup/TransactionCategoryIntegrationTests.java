package br.com.finup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.AiModel;
import br.com.finup.model.Category;
import br.com.finup.model.PaymentMethod;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.repository.TransactionRepository;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.service.AiAction;
import br.com.finup.service.RegisterTransactionAiAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** HTTP, autorizacao e persistencia reais; apenas o decoder JWT e simulado, sem chamadas a AWS. */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:transaction-category;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cors.allowed-origins=http://localhost:5173"
    })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TransactionCategoryIntegrationTests {
  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private TransactionRepository transactions;
  @Autowired private EntityManager em;
  @Autowired private ObjectMapper json;
  @Autowired private RegisterTransactionAiAction registerAi;
  @MockitoBean private JwtDecoder decoder;

  private User user;
  private Category leisure;
  private Category education;

  @BeforeEach
  void setUp() {
    user = users.saveAndFlush(User.createFromCognitoIdentity("sub-edit", "Ana", "ana@edit.test"));
    leisure =
        categories.saveAndFlush(Category.createForUser(user, "Lazer", TransactionType.EXPENSE));
    education =
        categories.saveAndFlush(Category.createForUser(user, "Educação", TransactionType.EXPENSE));
    when(decoder.decode("user"))
        .thenReturn(
            Jwt.withTokenValue("user")
                .header("alg", "RS256")
                .subject(user.getCognitoId())
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
  }

  @Test
  void editsExpenseCreatedThroughManualEndpointAndPersistsAfterReload() throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/transactions")
                    .header("Authorization", "Bearer user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
            {"categoryId":"%s","type":"EXPENSE","amount":1.23,
             "transactionDate":"2026-11-12","isRecurring":false}
            """
                            .formatted(leisure.getId())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(json.readTree(response).path("id").asText());
    change(id, education.getId(), 200);
    em.clear();
    assertThat(transactions.findById(id).orElseThrow().getCategoryId())
        .isEqualTo(education.getId());
    mvc.perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer user")
                .param("from", "2026-11-12")
                .param("to", "2026-11-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(-1.23))
        .andExpect(jsonPath("$.transactions[0].categoryId").value(education.getId().toString()));
    change(id, leisure.getId(), 200);
    em.clear();
    assertThat(transactions.findById(id).orElseThrow().getCategoryId()).isEqualTo(leisure.getId());
  }

  @Test
  void editsExpenseUsingCategoryCreatedByUserWithoutCreatingCategories() throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/categories")
                    .header("Authorization", "Bearer user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
            {"name":"Manutenção da bicicleta","type":"EXPENSE"}
            """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID categoryId = UUID.fromString(json.readTree(response).path("id").asText());
    long categoryCount = categories.count();
    Transaction expense = expense(user, leisure, TransactionType.EXPENSE);

    change(expense.getId(), categoryId, 200);
    em.clear();

    assertThat(transactions.findById(expense.getId()).orElseThrow().getCategoryId())
        .isEqualTo(categoryId);
    Category selected = categories.findById(categoryId).orElseThrow();
    assertThat(selected.getName()).isEqualTo("Manutenção da bicicleta");
    assertThat(selected.getUser().getId()).isEqualTo(user.getId());
    assertThat(selected.isDefault()).isFalse();
    assertThat(categories.count()).isEqualTo(categoryCount);
  }

  @Test
  void editsExpenseRegisteredByAiWithoutCallingBedrock() throws Exception {
    Transaction expense =
        registerAi
            .execute(
                new AiAction.Context(
                    new AuthenticatedIdentity(user.getCognitoId(), null, null),
                    user,
                    List.of(leisure, education),
                    "Gastei 7 reais num livro",
                    LocalDate.of(2026, 11, 12),
                    AiModel.AMAZON_LITE),
                json.readTree(
                    """
            {"type":"EXPENSE","amount":7,"category":"Educação","description":"Livro","date":null}
            """))
            .transaction();
    change(expense.getId(), leisure.getId(), 200);
    em.clear();
    Transaction saved = transactions.findById(expense.getId()).orElseThrow();
    assertThat(saved.getCategoryId()).isEqualTo(leisure.getId());
    assertThat(saved.getDescription()).isEqualTo("Livro");
    assertThat(saved.getAmount()).isEqualByComparingTo("7.00");
  }

  @Test
  void preservesPaymentDescriptionAmountDatesAndRecurrence() throws Exception {
    PaymentMethod payment =
        new PaymentMethod(user.getId(), "DEBIT", "Conta", "Banco", null, null, null, true);
    em.persist(payment);
    Transaction expense =
        transactions.saveAndFlush(
            Transaction.register(
                user.getId(),
                leisure.getId(),
                payment.getId(),
                TransactionType.EXPENSE,
                "Mensalidade",
                new BigDecimal("123.45"),
                LocalDate.of(2026, 9, 30),
                RecurrenceFrequency.MONTHLY));
    UUID id = expense.getId();
    em.clear();
    Transaction before = transactions.findById(id).orElseThrow();
    Instant createdAt = before.getCreatedAt();
    Instant updatedAt = before.getUpdatedAt();
    em.clear();
    change(id, education.getId(), 200);
    em.clear();
    Transaction saved = transactions.findById(id).orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
    assertThat(saved.getPaymentMethodId()).isEqualTo(payment.getId());
    assertThat(saved.getDescription()).isEqualTo("Mensalidade");
    assertThat(saved.getAmount()).isEqualByComparingTo("123.45");
    assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
    assertThat(saved.getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(saved.isRecurring()).isTrue();
    assertThat(saved.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
    assertThat(saved.getLastOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(saved.getRecurrenceOriginId()).isNull();
    assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
    assertThat(saved.getUpdatedAt()).isAfter(updatedAt);
  }

  @Test
  void acceptsSystemDefaultCategoryAndDoesNotChangeOtherTransactions() throws Exception {
    Transaction expense = expense(user, leisure, TransactionType.EXPENSE);
    Transaction another = expense(user, leisure, TransactionType.EXPENSE);
    UUID defaultId = UUID.randomUUID();
    em.createNativeQuery(
            """
        INSERT INTO categories (id,name,type,is_default,created_at,updated_at)
        VALUES (:id,'Categoria padrao','EXPENSE',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """)
        .setParameter("id", defaultId)
        .executeUpdate();
    change(expense.getId(), defaultId, 200);
    em.clear();
    assertThat(transactions.findById(expense.getId()).orElseThrow().getCategoryId())
        .isEqualTo(defaultId);
    assertThat(transactions.findById(another.getId()).orElseThrow().getCategoryId())
        .isEqualTo(leisure.getId());
  }

  @Test
  void rejectsAnotherUsersTransactionAndMissingTransaction() throws Exception {
    User other =
        users.saveAndFlush(User.createFromCognitoIdentity("sub-other", "Bia", "bia@edit.test"));
    Transaction expense = expense(other, leisure, TransactionType.EXPENSE);
    change(expense.getId(), education.getId(), 404);
    change(UUID.randomUUID(), education.getId(), 404);
    em.clear();
    assertThat(transactions.findById(expense.getId()).orElseThrow().getCategoryId())
        .isEqualTo(leisure.getId());
  }

  @Test
  void rejectsPrivateCategoryOfAnotherUserAndMissingCategory() throws Exception {
    User other =
        users.saveAndFlush(User.createFromCognitoIdentity("sub-other", "Bia", "bia@edit.test"));
    Category privateIncome =
        categories.saveAndFlush(Category.createForUser(other, "Privada", TransactionType.INCOME));
    Transaction expense = expense(user, leisure, TransactionType.EXPENSE);
    change(expense.getId(), privateIncome.getId(), 404);
    change(expense.getId(), UUID.randomUUID(), 404);
    assertThat(transactions.findById(expense.getId()).orElseThrow().getCategoryId())
        .isEqualTo(leisure.getId());
  }

  @Test
  void rejectsIncomeCategoryAndIncomeTransaction() throws Exception {
    Category income =
        categories.saveAndFlush(Category.createForUser(user, "Salario", TransactionType.INCOME));
    Transaction expense = expense(user, leisure, TransactionType.EXPENSE);
    change(expense.getId(), income.getId(), 422);
    change(expense(user, income, TransactionType.INCOME).getId(), education.getId(), 422);
    assertThat(transactions.findById(expense.getId()).orElseThrow().getCategoryId())
        .isEqualTo(leisure.getId());
  }

  @Test
  void requiresAuthenticationAndValidBody() throws Exception {
    Transaction expense = expense(user, leisure, TransactionType.EXPENSE);
    mvc.perform(
            patch(path(expense.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + education.getId() + "\"}"))
        .andExpect(status().isUnauthorized());
    for (String body : List.of("{}", "{\"categoryId\":null}", "{\"categoryId\":\"invalid\"}")) {
      mvc.perform(
              patch(path(expense.getId()))
                  .header("Authorization", "Bearer user")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void retryWithAlreadySavedCategoryDoesNotCreateAnotherTransaction() throws Exception {
    Transaction expense = expense(user, leisure, TransactionType.EXPENSE);
    change(expense.getId(), education.getId(), 200);
    em.clear();
    Instant savedAt = transactions.findById(expense.getId()).orElseThrow().getUpdatedAt();
    change(expense.getId(), education.getId(), 200);
    em.clear();
    assertThat(transactions.count()).isEqualTo(1);
    assertThat(transactions.findById(expense.getId()).orElseThrow().getUpdatedAt())
        .isEqualTo(savedAt);
  }

  @Test
  void ignoresExtraFinancialFieldsAndUsesOnlyCategoryId() throws Exception {
    Transaction expense = expense(user, leisure, TransactionType.EXPENSE);
    mvc.perform(
            patch(path(expense.getId()))
                .header("Authorization", "Bearer user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"categoryId":"%s","amount":999,"description":"alterada","isRecurring":true,
             "transactionDate":"2000-01-01","userId":"%s"}
            """
                        .formatted(education.getId(), UUID.randomUUID())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(1.23))
        .andExpect(jsonPath("$.description").value("Teste"))
        .andExpect(jsonPath("$.transactionDate").value("2026-11-12"))
        .andExpect(jsonPath("$.isRecurring").value(false));
  }

  private Transaction expense(User owner, Category category, TransactionType type) {
    return transactions.saveAndFlush(
        Transaction.register(
            owner.getId(),
            category.getId(),
            null,
            type,
            "Teste",
            new BigDecimal("1.23"),
            LocalDate.of(2026, 11, 12),
            null));
  }

  private void change(UUID transactionId, UUID categoryId, int status) throws Exception {
    mvc.perform(
            patch(path(transactionId))
                .header("Authorization", "Bearer user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + categoryId + "\"}"))
        .andExpect(status().is(status));
  }

  private String path(UUID id) {
    return "/api/v1/transactions/" + id + "/category";
  }
}
