package br.com.finup;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.exception.AiProviderException;
import br.com.finup.model.Category;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.repository.TransactionRepository;
import br.com.finup.repository.UserRepository;
import br.com.finup.service.BedrockLlmService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/insights/spending-alerts} de ponta a ponta: HTTP, segurança, service e
 * persistência reais. Só o decoder JWT e o Bedrock são simulados, sem AWS ou rede.
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:spending-alerts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "finup.cognito.user-pool-id=us-east-1_TestPool",
      "finup.cognito.client-id=test-client",
      "finup.cors.allowed-origins=http://localhost:5173"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpendingAlertIntegrationTests {

  private static final String ALERTS = "/api/v1/insights/spending-alerts";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private TransactionRepository transactions;
  @Autowired private Clock clock;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private BedrockLlmService llmService;

  private User user;
  private Category bets;
  private Category leisure;
  private LocalDate today;

  @BeforeEach
  void setUp() {
    today = LocalDate.now(clock);
    user = users.saveAndFlush(User.createFromCognitoIdentity("sub-alerts", "Ana", "ana@x.com"));
    bets =
        categories.saveAndFlush(Category.createForUser(user, "Apostas", TransactionType.EXPENSE));
    leisure =
        categories.saveAndFlush(Category.createForUser(user, "Lazer", TransactionType.EXPENSE));
    when(jwtDecoder.decode("user")).thenReturn(token("sub-alerts"));
  }

  @Test
  void combinesRuleAndAiAlertsWithSingleBedrockCall() throws Exception {
    expense(bets, "Bet365", "1000.00", today);
    expense(bets, "Bet365", "2000.00", today.minusDays(29));
    expense(bets, "Fora do periodo", "500.00", today.minusDays(30));
    income(bets, "Premio", "800.00", today);
    expense(leisure, "Cinema", "40.00", today);
    expense(leisure, "Rifa de moto", "100.00", today.minusDays(1));
    expense(leisure, "Rifa de moto", "100.00", today.minusDays(2));
    otherUsersExpense("5000.00");
    when(llmService.invoke(
            any(),
            eq(
                "1. Categoria: lazer | Descrição: cinema\n2. Categoria: lazer | Descrição: rifa de moto\n"),
            any()))
        .thenReturn("{\"risky\": [2]}");

    mvc.perform(user(get(ALERTS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.periodDays").value(30))
        .andExpect(jsonPath("$.alerts", hasSize(2)))
        .andExpect(jsonPath("$.alerts[0].categoryId").value(bets.getId().toString()))
        .andExpect(jsonPath("$.alerts[0].categoryName").value("Apostas"))
        .andExpect(jsonPath("$.alerts[0].riskKind").value("BETS"))
        .andExpect(jsonPath("$.alerts[0].totalAmount").value(3000.00))
        .andExpect(jsonPath("$.alerts[0].transactionCount").value(2))
        .andExpect(
            jsonPath("$.alerts[0].message").value("R$ 3.000,00 em apostas nos últimos 30 dias."))
        .andExpect(jsonPath("$.alerts[0].projection.years").value(5))
        .andExpect(jsonPath("$.alerts[0].projection.annualRatePct").value(10))
        .andExpect(jsonPath("$.alerts[0].projection.futureValue").value(4831.53))
        .andExpect(jsonPath("$.alerts[0].source").value("RULE"))
        .andExpect(jsonPath("$.alerts[1].categoryName").value("Lazer"))
        .andExpect(jsonPath("$.alerts[1].riskKind").value("OTHER"))
        .andExpect(jsonPath("$.alerts[1].totalAmount").value(200.00))
        .andExpect(jsonPath("$.alerts[1].transactionCount").value(2))
        .andExpect(jsonPath("$.alerts[1].source").value("AI"));

    mvc.perform(user(get(ALERTS))).andExpect(status().isOk());
    verify(llmService, times(1)).invoke(any(), any(), any());
  }

  @Test
  void aiFailureReturnsOnlyRuleAlerts() throws Exception {
    expense(bets, "Bet365", "100.00", today);
    expense(leisure, "Rifa de moto", "100.00", today);
    when(llmService.invoke(any(), any(), any()))
        .thenThrow(new AiProviderException("indisponivel", HttpStatus.SERVICE_UNAVAILABLE, null));

    mvc.perform(user(get(ALERTS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alerts", hasSize(1)))
        .andExpect(jsonPath("$.alerts[0].source").value("RULE"));
  }

  @Test
  void withoutRiskySpendingReturnsEmptyAlerts() throws Exception {
    otherUsersExpense("5000.00");

    mvc.perform(user(get(ALERTS).param("days", "7")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.periodDays").value(7))
        .andExpect(jsonPath("$.alerts", hasSize(0)));
  }

  @Test
  void invalidDaysReturns400ProblemDetail() throws Exception {
    mvc.perform(user(get(ALERTS).param("days", "366")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mvc.perform(user(get(ALERTS).param("days", "abc"))).andExpect(status().isBadRequest());
  }

  private void expense(Category category, String description, String amount, LocalDate date) {
    save(user, category, TransactionType.EXPENSE, description, amount, date);
  }

  private void income(Category category, String description, String amount, LocalDate date) {
    save(user, category, TransactionType.INCOME, description, amount, date);
  }

  private void otherUsersExpense(String amount) {
    User other = users.saveAndFlush(User.createFromCognitoIdentity("sub-other", "Bia", "b@x.com"));
    Category otherBets =
        categories.saveAndFlush(Category.createForUser(other, "Apostas", TransactionType.EXPENSE));
    save(other, otherBets, TransactionType.EXPENSE, "Bet365", amount, today);
  }

  private void save(
      User owner,
      Category category,
      TransactionType type,
      String description,
      String amount,
      LocalDate date) {
    transactions.saveAndFlush(
        Transaction.register(
            owner.getId(),
            category.getId(),
            null,
            type,
            description,
            new BigDecimal(amount),
            date,
            null));
  }

  private static MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer user");
  }

  private static Jwt token(String subject) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("user")
        .header("alg", "RS256")
        .subject(subject)
        .issuedAt(now.minusSeconds(60))
        .expiresAt(now.plusSeconds(3600))
        .build();
  }
}
