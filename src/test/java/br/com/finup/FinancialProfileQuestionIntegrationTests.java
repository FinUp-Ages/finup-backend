package br.com.finup;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.model.FinancialProfileAnswerOption;
import br.com.finup.model.FinancialProfileQuestion;
import br.com.finup.repository.FinancialProfileQuestionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura das perguntas de perfil financeiro pelo app, com HTTP, seguranca, service e persistencia
 * reais. So o decoder JWT e simulado, com um usuario comum (sem grupo admin) — a rota nao e de
 * admin.
 */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:financial-profile-questions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class FinancialProfileQuestionIntegrationTests {

  private static final String ROOT = "/api/v1/financial-profile/questions";

  @Autowired private MockMvc mvc;
  @Autowired private FinancialProfileQuestionRepository questions;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    FinancialProfileQuestion habit =
        new FinancialProfileQuestion("SPENDING_HABIT", "Como voce gasta?", 2);
    new FinancialProfileAnswerOption(habit, "CONTROLLED", "Controlado", new BigDecimal("100"), 1);
    new FinancialProfileAnswerOption(habit, "IMPULSIVE", "Impulsivo", new BigDecimal("20"), 2);

    FinancialProfileQuestion incomeExpense =
        new FinancialProfileQuestion("INCOME_EXPENSE_RELATION", "Renda x gastos?", 1);
    new FinancialProfileAnswerOption(
        incomeExpense, "SPENDS_LESS", "Gasto menos", new BigDecimal("0.25"), 1);

    questions.saveAll(java.util.List.of(habit, incomeExpense));
    questions.flush();
    when(jwtDecoder.decode("user")).thenReturn(userToken());
  }

  @Test
  void listsQuestionsOrderedWithOptionsForAuthenticatedUser() throws Exception {
    mvc.perform(user(get(ROOT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].code").value("INCOME_EXPENSE_RELATION"))
        .andExpect(jsonPath("$[0].options[0].code").value("SPENDS_LESS"))
        .andExpect(jsonPath("$[0].options[0].weight").value(0.25))
        .andExpect(jsonPath("$[1].code").value("SPENDING_HABIT"))
        .andExpect(jsonPath("$[1].options", hasSize(2)))
        .andExpect(jsonPath("$[1].options[0].code").value("CONTROLLED"))
        .andExpect(jsonPath("$[1].options[1].code").value("IMPULSIVE"));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
  }

  private static MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder call) {
    return call.header("Authorization", "Bearer user");
  }

  private static Jwt userToken() {
    Instant now = Instant.now();
    return Jwt.withTokenValue("user")
        .header("alg", "RS256")
        .subject("app-user")
        .issuedAt(now.minusSeconds(60))
        .expiresAt(now.plusSeconds(3600))
        .build();
  }
}
