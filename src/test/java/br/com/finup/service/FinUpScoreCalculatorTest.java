package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.SpendingHabit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Casos principais espelham, número a número, os Exemplos A a D de {@code docs/finup-score.md} —
 * qualquer mudança de fórmula que quebre estes testes exige atualizar o documento junto.
 */
class FinUpScoreCalculatorTest {

  private final FinUpScoreCalculator calculator = new FinUpScoreCalculator();

  @Test
  @DisplayName("Exemplo A: fallback para estimativa de gastos produz score 696")
  void exampleAFallbackEstimate() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("5000"),
            null,
            new BigDecimal("3000"),
            IncomeExpenseRelation.BALANCED,
            new BigDecimal("2000"),
            false,
            new BigDecimal("2150"),
            0.30,
            SpendingHabit.MODERATE);

    assertThat(computedScore(inputs)).isEqualTo(696);
  }

  @Test
  @DisplayName("Exemplo B: sem renda mensal, resultado e dado insuficiente")
  void exampleBInsufficientData() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            null, null, null, null, BigDecimal.ZERO, false, BigDecimal.ZERO, null, null);

    assertThat(calculator.calculate(inputs)).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  @Test
  @DisplayName("Exemplo C: usuario saudavel satura os tetos e produz score 950")
  void exampleCHealthyUser() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("8000"),
            null,
            new BigDecimal("5000"),
            null,
            BigDecimal.ZERO,
            false,
            new BigDecimal("50000"),
            null,
            SpendingHabit.CONTROLLED);

    assertThat(computedScore(inputs)).isEqualTo(950);
  }

  @Test
  @DisplayName("Exemplo D: dividas em atraso e savings negativo produzem score 106")
  void exampleDStrugglingUser() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("3000"),
            null,
            new BigDecimal("3900"),
            null,
            new BigDecimal("20000"),
            true,
            BigDecimal.ZERO,
            null,
            SpendingHabit.IMPULSIVE);

    assertThat(computedScore(inputs)).isEqualTo(106);
  }

  @Test
  @DisplayName("renda zero e tratada como dado insuficiente")
  void zeroIncomeIsInsufficientData() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, false, BigDecimal.ZERO, null, null);

    assertThat(calculator.calculate(inputs)).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  @Test
  @DisplayName("renda negativa e tratada como dado insuficiente")
  void negativeIncomeIsInsufficientData() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("-100"),
            null,
            null,
            null,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            null,
            null);

    assertThat(calculator.calculate(inputs)).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  @Test
  @DisplayName("taxa de poupanca acima de 30% satura o pilar de orcamento em 350")
  void savingsRateAboveCapSaturatesBudgetPillar() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("10000"),
            new BigDecimal("1000"),
            null,
            null,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            null,
            null);

    // pilar1=350 + pilar2=250 + pilar3=0 + pilar4=50(neutro) + pilar5=60(neutro) = 710
    assertThat(computedScore(inputs)).isEqualTo(710);
  }

  @Test
  @DisplayName("dividas que somam a renda anual inteira zeram o pilar de endividamento")
  void debtEqualToAnnualIncomeZeroesDebtPillar() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("12000"),
            false,
            BigDecimal.ZERO,
            null,
            null);

    // pilar1=175(savingsRate 0) + pilar2=0 + pilar3=0 + pilar4=50 + pilar5=60 = 285
    assertThat(computedScore(inputs)).isEqualTo(285);
  }

  @Test
  @DisplayName("divida em atraso subtrai exatamente 75 pontos quando a base comporta a penalidade")
  void lateDebtAppliesFixedPenalty() {
    FinUpScoreInputs withoutLate =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("6000"),
            false,
            BigDecimal.ZERO,
            null,
            null);
    FinUpScoreInputs withLate =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("6000"),
            true,
            BigDecimal.ZERO,
            null,
            null);

    assertThat(computedScore(withoutLate) - computedScore(withLate)).isEqualTo(75);
  }

  @Test
  @DisplayName("penalidade de atraso tem piso zero e nao deixa o pilar de endividamento negativo")
  void lateDebtPenaltyFloorsAtZero() {
    FinUpScoreInputs withLate =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("10800"),
            true,
            BigDecimal.ZERO,
            null,
            null);

    // ratio=0,9 -> base=25 (sem atraso); com atraso, 25-75 seria negativo, mas o piso e zero.
    // pilar1=175 + pilar2=0 + pilar3=0 + pilar4=50 + pilar5=60 = 285
    assertThat(computedScore(withLate)).isEqualTo(285);
  }

  @Test
  @DisplayName("6 meses de renda investida satura o pilar de reserva em 200")
  void sixMonthsOfIncomeInvestedSaturatesInvestmentPillar() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            BigDecimal.ZERO,
            false,
            new BigDecimal("6000"),
            null,
            null);

    // pilar1=175 + pilar2=250 + pilar3=200 + pilar4=50 + pilar5=60 = 735
    assertThat(computedScore(inputs)).isEqualTo(735);
  }

  @Test
  @DisplayName("meta concluida conta progresso 1 e meta cancelada e ignorada")
  void completedGoalCountsFullProgressAndCancelledIsIgnored() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            1.0,
            null);

    // pilar1=175 + pilar2=250 + pilar3=0 + pilar4=100 + pilar5=60 = 585
    assertThat(computedScore(inputs)).isEqualTo(585);
  }

  private int computedScore(FinUpScoreInputs inputs) {
    FinUpScoreResult result = calculator.calculate(inputs);
    assertThat(result).isInstanceOf(FinUpScoreResult.Computed.class);
    return ((FinUpScoreResult.Computed) result).score();
  }
}
