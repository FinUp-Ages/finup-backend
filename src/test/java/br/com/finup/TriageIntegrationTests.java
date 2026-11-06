package br.com.finup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.FinancialProfile;
import br.com.finup.model.User;
import br.com.finup.model.UserFinancialProfile;
import br.com.finup.repository.RiskAssessmentRepository;
import br.com.finup.repository.TriageResponseRepository;
import br.com.finup.repository.UserFinancialProfileRepository;
import br.com.finup.repository.UserInvestmentInterestRepository;
import br.com.finup.repository.UserRepository;
import br.com.finup.service.BedrockLlmService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triagem de ponta a ponta: HTTP, segurança, validação, persistência e recálculo do score reais. Só
 * o decoder JWT e o Bedrock são simulados.
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:triage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
@ExtendWith(OutputCaptureExtension.class)
class TriageIntegrationTests {

  private static final String QUESTIONS = "/api/v1/triage/questions";
  private static final String ANSWERS = "/api/v1/triage/answers";
  private static final String ASSESSMENT = "/api/v1/triage/assessment";

  /** Quantia que não pode aparecer em log. */
  private static final String SECRET_AMOUNT = "98765.43";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private UserFinancialProfileRepository profiles;
  @Autowired private UserInvestmentInterestRepository interests;
  @Autowired private TriageResponseRepository responses;
  @Autowired private RiskAssessmentRepository assessments;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private BedrockLlmService llmService;

  private User ana;

  @BeforeEach
  void setUp() {
    ana = users.saveAndFlush(User.createFromCognitoIdentity("sub-ana", "Ana", "ana@x.com"));
    UserFinancialProfile profile = UserFinancialProfile.createFor(ana);
    profile.apply(null, null, null, new BigDecimal("5000"));
    profiles.saveAndFlush(profile);
    when(jwtDecoder.decode("ana")).thenReturn(token("ana", "sub-ana"));
    when(jwtDecoder.decode("bia")).thenReturn(token("bia", "sub-bia"));
    when(jwtDecoder.decode("ghost")).thenReturn(token("ghost", "sub-ghost"));
  }

  @Test
  void questionnaireIsVersionedAndRequiresToken() throws Exception {
    mvc.perform(as("ana", get(QUESTIONS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.questions", hasSize(27)))
        .andExpect(jsonPath("$.questions[0].id").value("INCOME_TYPE"))
        .andExpect(jsonPath("$.questions[0].type").value("SINGLE_CHOICE"))
        .andExpect(jsonPath("$.questions[0].options", hasSize(3)))
        .andExpect(jsonPath("$.questions[1].decimals").value(0));

    mvc.perform(get(QUESTIONS)).andExpect(status().isUnauthorized());
    mvc.perform(post(ANSWERS).contentType(MediaType.APPLICATION_JSON).content(full()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(ASSESSMENT)).andExpect(status().isUnauthorized());
    mvc.perform(get(ANSWERS)).andExpect(status().isUnauthorized());
  }

  @Test
  void submitReturns201WithAssessmentAndPersistsEverything() throws Exception {
    mvc.perform(as("ana", submit(full())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assessment.profile").value("AGGRESSIVE"))
        .andExpect(
            jsonPath("$.assessment.summary").value(startsWith("Perfil de investidor arrojado.")))
        .andExpect(jsonPath("$.assessment.risks[0].severity").value("HIGH"))
        .andExpect(jsonPath("$.assessment.risks[0].code").isNotEmpty())
        .andExpect(jsonPath("$.assessment.risks[0].title").isNotEmpty())
        .andExpect(jsonPath("$.assessment.risks[0].description").isNotEmpty())
        .andExpect(jsonPath("$.assessment.risks[0].suggestion").isNotEmpty())
        .andExpect(jsonPath("$.completedAt").isNotEmpty());

    assertThat(responses.findAll()).hasSize(1);
    assertThat(assessments.findAll()).hasSize(1);
    UserFinancialProfile profile = profiles.findByUserId(ana.getId()).orElseThrow();
    assertThat(profile.getMonthlyExpensesEstimate()).isEqualByComparingTo("3000.50");
    assertThat(profile.getIncomeExpenseRelation().name()).isEqualTo("SPENDS_MORE");
    assertThat(profile.getSpendingHabit().name()).isEqualTo("IMPULSIVE");
    assertThat(profile.getHasInvestments()).isTrue();
    assertThat(profile.getApproximateInvestedAmount()).isEqualByComparingTo("1500");
    assertThat(profile.getInvestmentExperienceTime()).isEqualTo("LESS_THAN_1_YEAR");
    assertThat(profile.getMonthlyIncome()).isEqualByComparingTo("5000");
    assertThat(interests.findAll())
        .extracting("investmentType")
        .containsExactlyInAnyOrder("CDB", "CRYPTO");
    assertThat(ana.getFinancialProfile()).isEqualTo(FinancialProfile.AGGRESSIVE);
    assertThat(ana.getFinUpScore()).isNotNull();
  }

  @Test
  void resubmitCreatesNewRowAndTheLatestOneWins() throws Exception {
    mvc.perform(as("ana", submit(full()))).andExpect(status().isCreated());
    mvc.perform(as("ana", submit(simple("SELL_ALL", "false")))).andExpect(status().isCreated());

    assertThat(responses.findAll()).hasSize(2);
    assertThat(assessments.findAll()).hasSize(2);
    mvc.perform(as("ana", get(ASSESSMENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assessment.profile").value("CONSERVATIVE"))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
    mvc.perform(as("ana", get(ANSWERS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.answers.MARKET_DROP_REACTION").value("SELL_ALL"))
        .andExpect(jsonPath("$.answers.FINANCIAL_KNOWLEDGE").value("ADVANCED"))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
    // O reenvio sem investimento limpa o que o primeiro gravou no perfil e nos interesses.
    UserFinancialProfile profile = profiles.findByUserId(ana.getId()).orElseThrow();
    assertThat(profile.getHasInvestments()).isFalse();
    assertThat(profile.getApproximateInvestedAmount()).isNull();
    assertThat(interests.findAll()).isEmpty();
    assertThat(ana.getFinancialProfile()).isEqualTo(FinancialProfile.CONSERVATIVE);
  }

  @Test
  void invalidSubmissionReturns400WithFieldAndSavesNothing() throws Exception {
    String body = full().replace("\"INCOME_TYPE\":\"VARIABLE\"", "\"INCOME_TYPE\":\"DIARIA\"");

    mvc.perform(as("ana", submit(body)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.fields[0].field").value("answers.INCOME_TYPE"));

    assertThat(responses.findAll()).isEmpty();
    assertThat(assessments.findAll()).isEmpty();
    assertThat(profiles.findByUserId(ana.getId()).orElseThrow().getHasInvestments()).isNull();
    assertThat(ana.getFinancialProfile()).isNull();
  }

  @Test
  void staleVersionUnknownQuestionAndMissingRequiredAreRejected() throws Exception {
    mvc.perform(as("ana", submit(full().replace("\"version\":1", "\"version\":0"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("version"));
    mvc.perform(as("ana", submit(full().replace("\"CHILDREN_COUNT\":2,", "\"NOPE\":1,"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[?(@.field=='answers.NOPE')]").exists())
        .andExpect(jsonPath("$.fields[?(@.field=='answers.CHILDREN_COUNT')]").exists());
    mvc.perform(as("ana", submit("{\"version\":1,\"answers\":{}}")))
        .andExpect(status().isBadRequest());
    mvc.perform(as("ana", submit("{\"answers\":{\"HAS_DEBT\":false}}")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tooManyAnswersAreRejected() throws Exception {
    StringBuilder many = new StringBuilder("{\"version\":1,\"answers\":{");
    for (int i = 0; i < 51; i++) {
      many.append(i == 0 ? "" : ",").append("\"Q").append(i).append("\":1");
    }
    many.append("}}");

    mvc.perform(as("ana", submit(many.toString())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0].field").value("answers"));
    assertThat(responses.findAll()).isEmpty();
  }

  @Test
  void oversizedBodyReturns413() throws Exception {
    String padding = "x".repeat(17 * 1024);

    mvc.perform(
            as("ana", submit("{\"version\":1,\"answers\":{\"INCOME_TYPE\":\"" + padding + "\"}}")))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    assertThat(responses.findAll()).isEmpty();
  }

  @Test
  void neverAnsweredReturns404ForAssessmentAndAnswers() throws Exception {
    mvc.perform(as("ana", get(ASSESSMENT))).andExpect(status().isNotFound());
    mvc.perform(as("ana", get(ANSWERS))).andExpect(status().isNotFound());
  }

  @Test
  void eachUserOnlyReadsTheirOwnAnswersAndAssessment() throws Exception {
    users.saveAndFlush(User.createFromCognitoIdentity("sub-bia", "Bia", "bia@x.com"));
    mvc.perform(as("ana", submit(full()))).andExpect(status().isCreated());

    mvc.perform(as("bia", get(ASSESSMENT))).andExpect(status().isNotFound());
    mvc.perform(as("bia", get(ANSWERS))).andExpect(status().isNotFound());

    mvc.perform(as("bia", submit(simple("WAIT", "false")))).andExpect(status().isCreated());
    mvc.perform(as("ana", get(ASSESSMENT)))
        .andExpect(jsonPath("$.assessment.profile").value("AGGRESSIVE"));
    mvc.perform(as("bia", get(ASSESSMENT)))
        .andExpect(jsonPath("$.assessment.profile").value("MODERATE"));
    assertThat(responses.findAll()).hasSize(2);
  }

  @Test
  void userWithoutStepOneGets404() throws Exception {
    mvc.perform(as("ghost", submit(full()))).andExpect(status().isNotFound());
    mvc.perform(as("ghost", get(ASSESSMENT))).andExpect(status().isNotFound());
  }

  @Test
  void answersAreNeverLogged(CapturedOutput output) throws Exception {
    mvc.perform(as("ana", submit(full()))).andExpect(status().isCreated());
    mvc.perform(
            as(
                "ana",
                submit(full().replace("\"INCOME_TYPE\":\"VARIABLE\"", "\"INCOME_TYPE\":\"X\""))))
        .andExpect(status().isBadRequest());

    assertThat(output.getAll()).doesNotContain(SECRET_AMOUNT).doesNotContain("98765");
  }

  /** Envio completo: variável, com filhos, gasta mais, dívida de cartão, investe e se arrisca. */
  private static String full() {
    return """
        {"version":1,"answers":{
          "INCOME_TYPE":"VARIABLE","CHILDREN_COUNT":2,"DEPENDENTS_COUNT":1,
          "HOUSING_TYPE":"RENTED","EXTRA_INCOME":false,"MONTHLY_SAVINGS":0,
          "BUDGET_TRACKING":"NEVER","USES_CREDIT_CARD":true,"CARD_BILL_PAYMENT":"MINIMUM",
          "HAS_INSURANCE":false,"GOAL_HORIZON":"SHORT_TERM","FINANCIAL_KNOWLEDGE":"BEGINNER",
          "INCOME_EXPENSE_RELATION":"SPENDS_MORE","MONTHLY_EXPENSES":3000.50,
          "SPENDING_HABIT":"IMPULSIVE","HAS_DEBT":true,
          "DEBT_TYPES":["CREDIT_CARD","PERSONAL_LOAN"],"DEBT_TOTAL_AMOUNT":%s,
          "LATE_PAYMENTS_6M":true,"EMERGENCY_RESERVE":"NONE","INVESTS":true,
          "INVESTED_AMOUNT":1500,"INVESTMENT_TIME":"LESS_THAN_1_YEAR",
          "INVESTMENT_INTERESTS":["CDB","CRYPTO"],"MAIN_GOAL":"PAY_DEBTS",
          "MARKET_DROP_REACTION":"BUY_MORE","RISKY_HABITS":["BETS","TOBACCO"]}}
        """
        .formatted(SECRET_AMOUNT);
  }

  /** Envio mínimo, sem dívida, sem investimento e sem hábitos de risco. */
  private static String simple(String reaction, String invests) {
    return """
        {"version":1,"answers":{
          "INCOME_TYPE":"FIXED","CHILDREN_COUNT":0,"DEPENDENTS_COUNT":0,
          "HOUSING_TYPE":"OWNED","EXTRA_INCOME":true,"MONTHLY_SAVINGS":500,
          "BUDGET_TRACKING":"ALWAYS","USES_CREDIT_CARD":false,"HAS_INSURANCE":true,
          "GOAL_HORIZON":"LONG_TERM","FINANCIAL_KNOWLEDGE":"ADVANCED",
          "INCOME_EXPENSE_RELATION":"SPENDS_LESS","MONTHLY_EXPENSES":1000,
          "SPENDING_HABIT":"CONTROLLED","HAS_DEBT":false,"LATE_PAYMENTS_6M":false,
          "EMERGENCY_RESERVE":"OVER_6_MONTHS","INVESTS":%s,"MAIN_GOAL":"RETIREMENT",
          "MARKET_DROP_REACTION":"%s"}}
        """
        .formatted(invests, reaction);
  }

  private static MockHttpServletRequestBuilder submit(String body) {
    return post(ANSWERS).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder r) {
    return r.header("Authorization", "Bearer " + token);
  }

  private static Jwt token(String value, String subject) {
    Instant now = Instant.now();
    return Jwt.withTokenValue(value)
        .header("alg", "RS256")
        .subject(subject)
        .issuedAt(now.minusSeconds(60))
        .expiresAt(now.plusSeconds(3600))
        .build();
  }
}
