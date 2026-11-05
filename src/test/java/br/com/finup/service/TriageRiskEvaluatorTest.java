package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.FinancialProfile;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.RiskSeverity;
import br.com.finup.model.SpendingHabit;
import br.com.finup.model.TriageAnswers;
import br.com.finup.model.TriageAnswers.DebtType;
import br.com.finup.model.TriageAnswers.EmergencyReserve;
import br.com.finup.model.TriageAnswers.IncomeType;
import br.com.finup.model.TriageAnswers.MarketDropReaction;
import br.com.finup.model.TriageAnswers.RiskyHabit;
import br.com.finup.model.TriageRisk;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cada regra tem um caso positivo e um negativo; os limiares da dívida têm os três degraus. */
class TriageRiskEvaluatorTest {

  private static final BigDecimal INCOME = new BigDecimal("5000");
  private static final BigDecimal ANNUAL = INCOME.multiply(BigDecimal.valueOf(12));

  private final TriageRiskEvaluator evaluator = new TriageRiskEvaluator();

  /** Respostas sem nenhum risco: cada teste muda só o que quer provar. */
  private static final class A {
    IncomeType incomeType = IncomeType.FIXED;
    int children = 0;
    int dependents = 0;
    IncomeExpenseRelation relation = IncomeExpenseRelation.BALANCED;
    SpendingHabit habit = SpendingHabit.CONTROLLED;
    boolean hasDebt = false;
    Set<DebtType> debtTypes = Set.of();
    BigDecimal debtTotal = null;
    boolean late = false;
    EmergencyReserve reserve = EmergencyReserve.FROM_3_TO_6_MONTHS;
    boolean invests = false;
    Set<String> interests = Set.of();
    MarketDropReaction reaction = MarketDropReaction.WAIT;
    Set<RiskyHabit> habits = Set.of();

    TriageAnswers build() {
      return new TriageAnswers(
          incomeType,
          children,
          dependents,
          relation,
          new BigDecimal("2000"),
          habit,
          hasDebt,
          debtTypes,
          debtTotal,
          late,
          reserve,
          invests,
          invests ? new BigDecimal("1000") : null,
          invests ? "OVER_3_YEARS" : null,
          interests,
          "RETIREMENT",
          reaction,
          habits);
    }
  }

  private List<TriageRisk> risks(UnaryOperator<A> change) {
    return evaluator.evaluate(change.apply(new A()).build(), INCOME);
  }

  private static List<String> codes(List<TriageRisk> risks) {
    return risks.stream().map(TriageRisk::code).toList();
  }

  @Test
  @DisplayName("respostas sem risco nao geram nenhum ponto de atencao")
  void baselineHasNoRisks() {
    assertThat(risks(a -> a)).isEmpty();
  }

  @Test
  @DisplayName("OVERSPENDING: gasta mais do que ganha e HIGH; gasta o mesmo nao")
  void overspending() {
    List<TriageRisk> positive =
        risks(
            a -> {
              a.relation = IncomeExpenseRelation.SPENDS_MORE;
              return a;
            });
    assertThat(codes(positive)).containsExactly("OVERSPENDING");
    assertThat(positive.get(0).severity()).isEqualTo(RiskSeverity.HIGH);
    assertThat(
            codes(
                risks(
                    a -> {
                      a.relation = IncomeExpenseRelation.BALANCED;
                      return a;
                    })))
        .doesNotContain("OVERSPENDING");
  }

  @Test
  @DisplayName("COSTLY_DEBT: so cartao e cheque especial contam")
  void costlyDebtOnlyForCardAndOverdraft() {
    assertThat(codes(risks(a -> debt(a, Set.of(DebtType.CREDIT_CARD), "100"))))
        .contains("COSTLY_DEBT");
    assertThat(codes(risks(a -> debt(a, Set.of(DebtType.OVERDRAFT), "100"))))
        .contains("COSTLY_DEBT");
    assertThat(
            codes(risks(a -> debt(a, Set.of(DebtType.FINANCING, DebtType.PERSONAL_LOAN), "90000"))))
        .doesNotContain("COSTLY_DEBT");
    assertThat(codes(risks(a -> a))).doesNotContain("COSTLY_DEBT");
  }

  @Test
  @DisplayName("COSTLY_DEBT: gravidade pela divida frente a renda anual, nos limiares nomeados")
  void costlyDebtSeverityFollowsAnnualIncome() {
    BigDecimal high = ANNUAL.multiply(TriageRiskEvaluator.DEBT_HIGH_RATIO);
    BigDecimal medium = ANNUAL.multiply(TriageRiskEvaluator.DEBT_MEDIUM_RATIO);

    assertThat(debtSeverity(high)).isEqualTo(RiskSeverity.HIGH);
    assertThat(debtSeverity(high.subtract(BigDecimal.ONE))).isEqualTo(RiskSeverity.MEDIUM);
    assertThat(debtSeverity(medium)).isEqualTo(RiskSeverity.MEDIUM);
    assertThat(debtSeverity(medium.subtract(BigDecimal.ONE))).isEqualTo(RiskSeverity.LOW);
  }

  @Test
  @DisplayName("COSTLY_DEBT: sem renda informada assume a gravidade padrao")
  void costlyDebtWithoutIncomeUsesDefaultSeverity() {
    A a = debt(new A(), Set.of(DebtType.CREDIT_CARD), "999999");
    assertThat(evaluator.evaluate(a.build(), null).get(0).severity())
        .isEqualTo(TriageRiskEvaluator.DEBT_WITHOUT_INCOME_SEVERITY);
    assertThat(evaluator.evaluate(a.build(), BigDecimal.ZERO).get(0).severity())
        .isEqualTo(TriageRiskEvaluator.DEBT_WITHOUT_INCOME_SEVERITY);
  }

  @Test
  @DisplayName("LATE_PAYMENTS: atraso e MEDIUM; sem atraso nao")
  void latePayments() {
    List<TriageRisk> positive =
        risks(
            a -> {
              a.late = true;
              return a;
            });
    assertThat(codes(positive)).containsExactly("LATE_PAYMENTS");
    assertThat(positive.get(0).severity()).isEqualTo(RiskSeverity.MEDIUM);
    assertThat(codes(risks(a -> a))).doesNotContain("LATE_PAYMENTS");
  }

  @Test
  @DisplayName("NO_EMERGENCY_RESERVE: sem reserva e MEDIUM; com ate 3 meses nao")
  void noEmergencyReserve() {
    List<TriageRisk> positive =
        risks(
            a -> {
              a.reserve = EmergencyReserve.NONE;
              return a;
            });
    assertThat(codes(positive)).containsExactly("NO_EMERGENCY_RESERVE");
    assertThat(positive.get(0).severity()).isEqualTo(RiskSeverity.MEDIUM);
    assertThat(
            codes(
                risks(
                    a -> {
                      a.reserve = EmergencyReserve.UP_TO_3_MONTHS;
                      return a;
                    })))
        .doesNotContain("NO_EMERGENCY_RESERVE");
  }

  @Test
  @DisplayName("DEPENDENTS_WITHOUT_RESERVE: filhos ou dependentes sem reserva e HIGH")
  void dependentsWithoutReserve() {
    for (UnaryOperator<A> change :
        List.<UnaryOperator<A>>of(
            a -> {
              a.children = 1;
              a.reserve = EmergencyReserve.NONE;
              return a;
            },
            a -> {
              a.dependents = 2;
              a.reserve = EmergencyReserve.NONE;
              return a;
            })) {
      List<TriageRisk> positive = risks(change);
      assertThat(codes(positive)).contains("DEPENDENTS_WITHOUT_RESERVE");
      assertThat(positive.get(0).code()).isEqualTo("DEPENDENTS_WITHOUT_RESERVE");
      assertThat(positive.get(0).severity()).isEqualTo(RiskSeverity.HIGH);
    }
    assertThat(
            codes(
                risks(
                    a -> {
                      a.children = 1;
                      return a;
                    })))
        .doesNotContain("DEPENDENTS_WITHOUT_RESERVE");
    assertThat(
            codes(
                risks(
                    a -> {
                      a.reserve = EmergencyReserve.NONE;
                      return a;
                    })))
        .doesNotContain("DEPENDENTS_WITHOUT_RESERVE");
  }

  @Test
  @DisplayName("UNSTABLE_INCOME: variavel e MEDIUM, mista e LOW, fixa nao")
  void unstableIncome() {
    assertThat(
            severityOf(
                "UNSTABLE_INCOME",
                a -> {
                  a.incomeType = IncomeType.VARIABLE;
                  return a;
                }))
        .isEqualTo(RiskSeverity.MEDIUM);
    assertThat(
            severityOf(
                "UNSTABLE_INCOME",
                a -> {
                  a.incomeType = IncomeType.MIXED;
                  return a;
                }))
        .isEqualTo(RiskSeverity.LOW);
    assertThat(
            codes(
                risks(
                    a -> {
                      a.incomeType = IncomeType.FIXED;
                      return a;
                    })))
        .doesNotContain("UNSTABLE_INCOME");
  }

  @Test
  @DisplayName("IMPULSIVE_SPENDING: impulsivo e MEDIUM; moderado nao")
  void impulsiveSpending() {
    assertThat(
            severityOf(
                "IMPULSIVE_SPENDING",
                a -> {
                  a.habit = SpendingHabit.IMPULSIVE;
                  return a;
                }))
        .isEqualTo(RiskSeverity.MEDIUM);
    assertThat(
            codes(
                risks(
                    a -> {
                      a.habit = SpendingHabit.MODERATE;
                      return a;
                    })))
        .doesNotContain("IMPULSIVE_SPENDING");
  }

  @Test
  @DisplayName("RISKY_HABITS: apostas e HIGH, bebida ou cigarro MEDIUM, nenhum nao gera risco")
  void riskyHabits() {
    assertThat(
            severityOf(
                "RISKY_HABITS",
                a -> {
                  a.habits = Set.of(RiskyHabit.BETS);
                  return a;
                }))
        .isEqualTo(RiskSeverity.HIGH);
    assertThat(
            severityOf(
                "RISKY_HABITS",
                a -> {
                  a.habits = Set.of(RiskyHabit.ALCOHOL);
                  return a;
                }))
        .isEqualTo(RiskSeverity.MEDIUM);
    assertThat(
            severityOf(
                "RISKY_HABITS",
                a -> {
                  a.habits = Set.of(RiskyHabit.TOBACCO);
                  return a;
                }))
        .isEqualTo(RiskSeverity.MEDIUM);
    assertThat(codes(risks(a -> a))).doesNotContain("RISKY_HABITS");
  }

  @Test
  @DisplayName("RISKY_INVESTING_WITHOUT_RESERVE: investe em risco sem reserva e HIGH")
  void riskyInvestingWithoutReserve() {
    assertThat(
            severityOf(
                "RISKY_INVESTING_WITHOUT_RESERVE",
                a -> {
                  a.invests = true;
                  a.reserve = EmergencyReserve.NONE;
                  a.reaction = MarketDropReaction.BUY_MORE;
                  return a;
                }))
        .isEqualTo(RiskSeverity.HIGH);
    assertThat(
            severityOf(
                "RISKY_INVESTING_WITHOUT_RESERVE",
                a -> {
                  a.invests = true;
                  a.reserve = EmergencyReserve.NONE;
                  a.interests = Set.of("CRYPTO");
                  return a;
                }))
        .isEqualTo(RiskSeverity.HIGH);
  }

  @Test
  @DisplayName(
      "RISKY_INVESTING_WITHOUT_RESERVE: nao dispara com reserva, sem investir ou sem apetite de risco")
  void riskyInvestingNegatives() {
    assertThat(
            codes(
                risks(
                    a -> {
                      a.invests = true;
                      a.reaction = MarketDropReaction.BUY_MORE;
                      return a;
                    })))
        .doesNotContain("RISKY_INVESTING_WITHOUT_RESERVE");
    assertThat(
            codes(
                risks(
                    a -> {
                      a.reserve = EmergencyReserve.NONE;
                      a.reaction = MarketDropReaction.BUY_MORE;
                      return a;
                    })))
        .doesNotContain("RISKY_INVESTING_WITHOUT_RESERVE");
    assertThat(
            codes(
                risks(
                    a -> {
                      a.invests = true;
                      a.reserve = EmergencyReserve.NONE;
                      a.interests = Set.of("CDB", "TREASURY");
                      return a;
                    })))
        .doesNotContain("RISKY_INVESTING_WITHOUT_RESERVE");
  }

  @Test
  @DisplayName("riscos saem da gravidade mais alta para a mais baixa")
  void ordersBySeverityDescending() {
    List<TriageRisk> result =
        risks(
            a -> {
              a.habit = SpendingHabit.IMPULSIVE;
              a.relation = IncomeExpenseRelation.SPENDS_MORE;
              a.incomeType = IncomeType.MIXED;
              return a;
            });

    assertThat(result)
        .extracting(TriageRisk::severity)
        .containsExactly(RiskSeverity.HIGH, RiskSeverity.MEDIUM, RiskSeverity.LOW);
  }

  @Test
  @DisplayName("o perfil de investidor vem da reacao a uma queda de 20%")
  void investorProfileFollowsMarketDropReaction() {
    assertThat(profileFor(MarketDropReaction.SELL_ALL)).isEqualTo(FinancialProfile.CONSERVATIVE);
    assertThat(profileFor(MarketDropReaction.WAIT)).isEqualTo(FinancialProfile.MODERATE);
    assertThat(profileFor(MarketDropReaction.BUY_MORE)).isEqualTo(FinancialProfile.AGGRESSIVE);
  }

  private FinancialProfile profileFor(MarketDropReaction reaction) {
    A a = new A();
    a.reaction = reaction;
    return evaluator.profileOf(a.build());
  }

  private RiskSeverity severityOf(String code, UnaryOperator<A> change) {
    return risks(change).stream()
        .filter(r -> r.code().equals(code))
        .findFirst()
        .orElseThrow(() -> new AssertionError("risco " + code + " nao gerado"))
        .severity();
  }

  private RiskSeverity debtSeverity(BigDecimal total) {
    return risks(
            a -> {
              debt(a, Set.of(DebtType.CREDIT_CARD), total.toPlainString());
              return a;
            })
        .get(0)
        .severity();
  }

  private static A debt(A a, Set<DebtType> types, String total) {
    a.hasDebt = true;
    a.debtTypes = types;
    a.debtTotal = new BigDecimal(total);
    return a;
  }
}
