package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.finup.exception.ApiExceptionHandler.InvalidField;
import br.com.finup.exception.InvalidTriageAnswersException;
import br.com.finup.model.TriageAnswers;
import br.com.finup.service.TriageAnswersValidator.Validated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TriageAnswersValidatorTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int VERSION = TriageQuestionnaire.VERSION;

  private final TriageAnswersValidator validator = new TriageAnswersValidator();

  /** Um envio completo e valido, sem dívida e sem investimento. */
  private static Map<String, JsonNode> valid() {
    Map<String, JsonNode> a = new LinkedHashMap<>();
    put(a, "INCOME_TYPE", "\"FIXED\"");
    put(a, "CHILDREN_COUNT", "1");
    put(a, "DEPENDENTS_COUNT", "0");
    put(a, "INCOME_EXPENSE_RELATION", "\"BALANCED\"");
    put(a, "MONTHLY_EXPENSES", "2500.50");
    put(a, "SPENDING_HABIT", "\"MODERATE\"");
    put(a, "HAS_DEBT", "false");
    put(a, "LATE_PAYMENTS_6M", "false");
    put(a, "EMERGENCY_RESERVE", "\"UP_TO_3_MONTHS\"");
    put(a, "INVESTS", "false");
    put(a, "HOUSING_TYPE", "\"RENTED\"");
    put(a, "EXTRA_INCOME", "false");
    put(a, "MONTHLY_SAVINGS", "300");
    put(a, "BUDGET_TRACKING", "\"SOMETIMES\"");
    put(a, "USES_CREDIT_CARD", "false");
    put(a, "HAS_INSURANCE", "false");
    put(a, "GOAL_HORIZON", "\"LONG_TERM\"");
    put(a, "FINANCIAL_KNOWLEDGE", "\"BEGINNER\"");
    put(a, "MAIN_GOAL", "\"RETIREMENT\"");
    put(a, "MARKET_DROP_REACTION", "\"WAIT\"");
    return a;
  }

  private static void put(Map<String, JsonNode> answers, String id, String json) {
    try {
      answers.put(id, JSON.readTree(json));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private List<InvalidField> errorsOf(int version, Map<String, JsonNode> answers) {
    return ((InvalidTriageAnswersException)
            catchThrowable(() -> validator.validate(version, answers)))
        .getFields();
  }

  private static Throwable catchThrowable(Runnable r) {
    try {
      r.run();
    } catch (Throwable t) {
      return t;
    }
    throw new AssertionError("era esperada uma InvalidTriageAnswersException");
  }

  @Test
  @DisplayName("envio completo e valido vira respostas tipadas e normalizadas")
  void validSubmissionIsTyped() {
    Validated result = validator.validate(VERSION, valid());

    TriageAnswers typed = result.typed();
    assertThat(typed.childrenCount()).isEqualTo(1);
    assertThat(typed.monthlyExpenses()).isEqualByComparingTo("2500.50");
    assertThat(typed.hasDebt()).isFalse();
    assertThat(typed.debtTypes()).isEmpty();
    assertThat(typed.riskyHabits()).isEmpty();
    assertThat(result.normalized()).containsEntry("INCOME_TYPE", "FIXED");
  }

  @Test
  @DisplayName("perguntas condicionais exigem resposta quando a que as liga e verdadeira")
  void conditionalQuestionsAreRequiredWhenTriggered() {
    Map<String, JsonNode> a = valid();
    put(a, "HAS_DEBT", "true");
    put(a, "INVESTS", "true");

    assertThat(errorsOf(VERSION, a))
        .extracting(InvalidField::field)
        .containsExactlyInAnyOrder(
            "answers.DEBT_TYPES",
            "answers.DEBT_TOTAL_AMOUNT",
            "answers.INVESTED_AMOUNT",
            "answers.INVESTMENT_TIME");
  }

  @Test
  @DisplayName("forma de pagar a fatura so e exigida para quem usa cartao de credito")
  void cardBillPaymentIsRequiredOnlyForCardUsers() {
    Map<String, JsonNode> a = valid();
    put(a, "USES_CREDIT_CARD", "true");

    assertThat(errorsOf(VERSION, a))
        .extracting(InvalidField::field)
        .containsExactly("answers.CARD_BILL_PAYMENT");

    put(a, "CARD_BILL_PAYMENT", "\"MINIMUM\"");
    assertThat(validator.validate(VERSION, a).normalized())
        .containsEntry("CARD_BILL_PAYMENT", "MINIMUM");
  }

  @Test
  @DisplayName("com divida e investimento informados, grava os valores condicionais")
  void conditionalAnswersAreKept() {
    Map<String, JsonNode> a = valid();
    put(a, "HAS_DEBT", "true");
    put(a, "DEBT_TYPES", "[\"CREDIT_CARD\",\"OVERDRAFT\"]");
    put(a, "DEBT_TOTAL_AMOUNT", "1200");
    put(a, "INVESTS", "true");
    put(a, "INVESTED_AMOUNT", "5000");
    put(a, "INVESTMENT_TIME", "\"ONE_TO_THREE_YEARS\"");
    put(a, "INVESTMENT_INTERESTS", "[\"CDB\",\"CRYPTO\"]");

    TriageAnswers typed = validator.validate(VERSION, a).typed();

    assertThat(typed.debtTypes()).hasSize(2);
    assertThat(typed.debtTotalAmount()).isEqualByComparingTo("1200");
    assertThat(typed.investedAmount()).isEqualByComparingTo("5000");
    assertThat(typed.investmentInterests()).containsExactlyInAnyOrder("CDB", "CRYPTO");
  }

  @Test
  @DisplayName("resposta de pergunta que nao se aplica e descartada, nao rejeitada")
  void inapplicableAnswersAreDropped() {
    Map<String, JsonNode> a = valid();
    put(a, "DEBT_TOTAL_AMOUNT", "9999");

    Validated result = validator.validate(VERSION, a);

    assertThat(result.normalized()).doesNotContainKey("DEBT_TOTAL_AMOUNT");
    assertThat(result.typed().debtTotalAmount()).isNull();
  }

  @Test
  @DisplayName("versao antiga do questionario e recusada apontando o campo version")
  void staleVersionIsRejected() {
    assertThat(errorsOf(VERSION + 1, valid())).extracting(InvalidField::field).contains("version");
  }

  @Test
  @DisplayName("pergunta desconhecida e recusada apontando o campo")
  void unknownQuestionIsRejected() {
    Map<String, JsonNode> a = valid();
    put(a, "NAO_EXISTE", "1");

    assertThat(errorsOf(VERSION, a))
        .containsExactly(new InvalidField("answers.NAO_EXISTE", "pergunta desconhecida"));
  }

  @Test
  @DisplayName("pergunta obrigatoria ausente ou nula e recusada")
  void missingRequiredIsRejected() {
    Map<String, JsonNode> a = valid();
    a.remove("INCOME_TYPE");
    put(a, "SPENDING_HABIT", "null");

    assertThat(errorsOf(VERSION, a))
        .extracting(InvalidField::field)
        .containsExactlyInAnyOrder("answers.INCOME_TYPE", "answers.SPENDING_HABIT");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "INCOME_TYPE|\"DAILY\"",
        "INCOME_TYPE|3",
        "CHILDREN_COUNT|21",
        "CHILDREN_COUNT|-1",
        "CHILDREN_COUNT|1.5",
        "CHILDREN_COUNT|\"2\"",
        "MONTHLY_EXPENSES|-0.01",
        "MONTHLY_EXPENSES|100000001",
        "MONTHLY_EXPENSES|10.123",
        "HAS_DEBT|\"true\"",
        "LATE_PAYMENTS_6M|1",
        "RISKY_HABITS|\"BETS\"",
        "RISKY_HABITS|[\"DRUGS\"]",
        "RISKY_HABITS|[\"BETS\",\"BETS\"]",
        "RISKY_HABITS|[1]"
      })
  @DisplayName("valor de tipo errado, opcao invalida ou fora da faixa e recusado no campo certo")
  void invalidValuesAreRejected(String id, String json) {
    Map<String, JsonNode> a = valid();
    put(a, id, json);

    assertThat(errorsOf(VERSION, a))
        .extracting(InvalidField::field)
        .containsExactly("answers." + id);
  }

  @Test
  @DisplayName("os motivos de erro nunca repetem o valor enviado")
  void errorMessagesDoNotEchoValues() {
    Map<String, JsonNode> a = valid();
    put(a, "MONTHLY_EXPENSES", "987654321.12");
    put(a, "INCOME_TYPE", "\"segredo-financeiro\"");

    assertThat(errorsOf(VERSION, a))
        .extracting(InvalidField::message)
        .noneMatch(m -> m.contains("987654321") || m.contains("segredo"));
  }

  @Test
  @DisplayName("reporta todas as falhas de uma vez")
  void reportsAllFailuresTogether() {
    Map<String, JsonNode> a = valid();
    put(a, "INCOME_TYPE", "\"X\"");
    put(a, "CHILDREN_COUNT", "99");

    assertThat(errorsOf(VERSION, a)).hasSize(2);
  }

  @Test
  @DisplayName("renda de ate duas casas decimais e aceita")
  void twoDecimalsAreAccepted() {
    Map<String, JsonNode> a = valid();
    put(a, "MONTHLY_EXPENSES", "100.10");

    assertThat(validator.validate(VERSION, a).typed().monthlyExpenses())
        .isEqualByComparingTo(new BigDecimal("100.10"));
  }

  @Test
  @DisplayName("sem nenhuma resposta, todas as obrigatorias aparecem")
  void emptyAnswersListEveryRequired() {
    assertThatThrownBy(() -> validator.validate(VERSION, Map.of()))
        .isInstanceOf(InvalidTriageAnswersException.class);
  }
}
